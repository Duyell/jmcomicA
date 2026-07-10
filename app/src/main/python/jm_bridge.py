# -*- coding: utf-8 -*-
"""
JMComic PDF Downloader - Python Bridge
"""

import os
import re
import shutil
import sys
import traceback
import io
import json


# ---- Block curl_cffi BEFORE any jmcomic import ----
# curl_cffi contains Windows .pyd native code that crashes Android ARM64.
# This meta_path hook raises ImportError on any curl_cffi import attempt,
# so jmcomic's existing fallback to plain requests activates cleanly.
from importlib.abc import MetaPathFinder

class _CurlCffiBlocker(MetaPathFinder):
    def find_spec(self, fullname, path=None, target=None):
        if fullname == "curl_cffi" or fullname.startswith("curl_cffi."):
            raise ImportError(
                "curl_cffi blocked: contains incompatible native code for Android"
            )
        return None

sys.meta_path.insert(0, _CurlCffiBlocker())
# --------------------------------------------------------------


def _open_image_via_android(fp) -> "PIL.Image":
    """
    Decode an image using Android's native BitmapFactory (supports WebP),
    re-encode as JPEG, and return a PIL Image.
    Accepts bytes (HTTP response body), str (filepath), or file-like object.
    """
    from android.graphics import BitmapFactory, Bitmap
    from java.io import ByteArrayOutputStream
    from PIL import Image

    # Normalize input to bytes
    if isinstance(fp, bytes):
        data = fp
    elif isinstance(fp, str):
        with open(fp, 'rb') as fh:
            data = fh.read()
    else:
        # BytesIO or similar
        pos = fp.tell()
        fp.seek(0)
        data = fp.read()
        fp.seek(pos)

    bitmap = BitmapFactory.decodeByteArray(data, 0, len(data))
    if bitmap is None:
        raise RuntimeError("Android BitmapFactory could not decode image")

    stream = ByteArrayOutputStream()
    try:
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
    finally:
        bitmap.recycle()

    jpeg_bytes = stream.toByteArray()
    stream.close()
    return Image.open(io.BytesIO(jpeg_bytes))


def download_album_as_pdf(album_id: str, output_dir: str) -> str:
    """
    Download a JM comic album and convert all images to a single PDF.
    Returns the path to the generated PDF file.
    """
    os.makedirs(output_dir, exist_ok=True)

    import jmcomic
    from jmcomic.jm_client_interface import JmImageResp

    # Capture scramble metadata per image filepath
    _image_meta = {}  # filepath -> num

    _orig_transfer_to = JmImageResp.transfer_to

    def _patched_transfer_to(self, path, scramble_id, decode_image=True, img_url=None):
        # Save raw scrambled WebP bytes
        with open(path, 'wb') as f:
            f.write(self.content)
        # Capture scramble params for post-processing
        if scramble_id is not None and img_url is not None:
            from jmcomic.jm_toolkit import JmImageTool
            _image_meta[path] = JmImageTool.get_num_by_url(scramble_id, img_url)

    JmImageResp.transfer_to = _patched_transfer_to

    option_text = """
client:
  impl: api
  postman:
    type: requests

dir_rule:
  rule: Bd_Aid
  base_dir: {output_dir}/downloads

plugins:
  after_album: []
""".format(output_dir=output_dir.replace('\\', '/'))

    option = jmcomic.create_option_by_str(option_text)
    album_id = str(album_id).strip()

    download_base = os.path.join(output_dir, "downloads")

    try:
        album, downloader = jmcomic.download_album(album_id, option)
        downloader.raise_if_has_exception()

        # Post-process: unscramble via Android Canvas API (no PIL needed)
        _unscramble_via_android(_image_meta)

        all_image_paths = _collect_images(download_base)

        if not all_image_paths:
            raise FileNotFoundError(
                "No images found for album_id={} in {}".format(album_id, download_base)
            )

        safe_title = _sanitize_filename(album.title)
        pdf_filename = "[JM{}] {}.pdf".format(album_id, safe_title)
        pdf_path = os.path.join(output_dir, pdf_filename)

        _images_to_pdf(all_image_paths, pdf_path)

        if not os.path.exists(pdf_path):
            raise FileNotFoundError("PDF not generated: " + pdf_path)

        return pdf_path

    finally:
        try:
            JmImageResp.transfer_to = _orig_transfer_to
        except Exception:
            pass
        _cleanup_dir(download_base)


def _unscramble_via_android(image_meta: dict):
    """
    Unscramble WebP images using Android's Bitmap + Canvas API.
    Reorders horizontal strips in-place, saves as JPEG.
    """
    import math
    from android.graphics import (
        BitmapFactory, Bitmap, Canvas, Paint, Rect, RectF,
    )
    from java.io import FileOutputStream

    for filepath, num in image_meta.items():
        if not os.path.exists(filepath):
            continue

        # Decode scrambled WebP via Android
        bitmap = BitmapFactory.decodeFile(filepath)
        if bitmap is None:
            print("unscramble_fail: decode failed for {}".format(filepath))
            continue

        w = bitmap.getWidth()
        h = bitmap.getHeight()

        if num <= 1:
            # Not scrambled or simple reverse — save as JPEG directly
            _save_bitmap_as_jpeg(bitmap, filepath)
            continue

        # Create output bitmap and canvas
        out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(out)
        paint = Paint()

        over = h % num
        for i in range(num):
            move = int(h / num)
            y_src = h - (move * (i + 1)) - over
            y_dst = move * i
            if i == 0:
                move += over
            else:
                y_dst += over

            src_rect = Rect(0, y_src, w, y_src + move)
            dst_rect = Rect(0, y_dst, w, y_dst + move)
            canvas.drawBitmap(bitmap, src_rect, dst_rect, paint)

        bitmap.recycle()

        # Save as JPEG, remove original WebP
        _save_bitmap_as_jpeg(out, filepath)
        out.recycle()
        print("unscramble: {} num={} ({}x{})".format(
            os.path.basename(filepath), num, w, h))


def _save_bitmap_as_jpeg(bitmap, webp_filepath: str):
    """Save an Android Bitmap as JPEG alongside the original file path."""
    from android.graphics import Bitmap
    from java.io import FileOutputStream

    jpg_path = webp_filepath[:-5] + '.jpg' if webp_filepath.endswith('.webp') else webp_filepath + '.jpg'
    stream = FileOutputStream(jpg_path)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    stream.close()
    # Remove original scrambled WebP
    try:
        os.remove(webp_filepath)
    except Exception:
        pass


def _collect_images(base_dir: str) -> list:
    """
    Recursively collect all image files under base_dir, sorted by name.
    """
    extensions = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
    images = []
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if os.path.splitext(f)[1].lower() in extensions:
                images.append(os.path.join(root, f))
    # Sort by filename for correct page order
    images.sort(key=lambda p: os.path.basename(p))
    return images


def _decode_image_via_android(filepath: str):
    """
    Use Android's native BitmapFactory to decode an image file.
    Falls back to JPEG re-encoding so Pillow can read it.
    Returns a path to a Pillow-compatible JPEG file.
    """
    from android.graphics import BitmapFactory, Bitmap
    from java.io import FileOutputStream

    # Decode using Android (supports WebP, JPEG, PNG, GIF, BMP natively)
    bitmap = BitmapFactory.decodeFile(filepath)
    if bitmap is None:
        raise RuntimeError("Android BitmapFactory could not decode: " + filepath)

    # Re-encode as JPEG (lossy but universally supported)
    jpeg_path = filepath + ".android_decode.jpg"
    stream = FileOutputStream(jpeg_path)
    try:
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    finally:
        stream.close()
        bitmap.recycle()

    return jpeg_path


def _images_to_pdf(image_paths: list, output_pdf: str):
    """
    Convert a list of image paths into a single PDF.
    Uses Pillow for JPEG/PNG, and Android BitmapFactory for WebP.
    """
    from PIL import Image, features

    webp_supported = features.check("webp")
    if not webp_supported:
        print("WARNING: Pillow WebP support unavailable, using Android decoder fallback.")

    image_objects = []
    skipped = []
    android_temp_files = []  # track temp JPEGs for cleanup

    for path in image_paths:
        open_path = path  # path Pillow will actually read
        try:
            img = Image.open(open_path)
            img.load()  # force decode to catch errors early
        except Exception as e:
            # If Pillow can't read it, try Android BitmapFactory
            try:
                jpeg_path = _decode_image_via_android(path)
                android_temp_files.append(jpeg_path)
                img = Image.open(jpeg_path)
                img.load()
                print("android_decode: {} -> JPEG ({}x{})".format(
                    path, img.width, img.height))
            except Exception as e2:
                skipped.append(path)
                try:
                    with open(path, 'rb') as f:
                        header = f.read(12)
                    print("skip: {} reason=Pillow:'{}' Android:'{}' header={!r}".format(
                        path, str(e), str(e2), header[:12]))
                except Exception:
                    print("skip: {} {}".format(path, str(e2)))
                continue

        try:
            # Convert to RGB if necessary
            if img.mode in ('RGBA', 'P', 'LA'):
                rgb_img = Image.new('RGB', img.size, (255, 255, 255))
                if img.mode == 'P':
                    img = img.convert('RGBA')
                rgb_img.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
                img.close()
                img = rgb_img
            elif img.mode != 'RGB':
                converted = img.convert('RGB')
                img.close()
                img = converted
            image_objects.append(img)
        except Exception as e:
            skipped.append(path)
            print("skip: {} (convert) {}".format(path, str(e)))

    # Clean up temp files
    for tmp in android_temp_files:
        try:
            os.remove(tmp)
        except Exception:
            pass

    if not image_objects:
        if skipped:
            raise RuntimeError(
                "no valid images to convert to PDF. {} files skipped (first: {}). "
                "WebP support: {}".format(len(skipped), skipped[0], webp_supported)
            )
        raise RuntimeError("no valid images to convert to PDF")

    # Save the first image, append the rest
    first = image_objects[0]
    rest = image_objects[1:]
    first.save(
        output_pdf,
        "PDF",
        save_all=True,
        append_images=rest,
        resolution=100.0,
    )


def _sanitize_filename(name: str) -> str:
    """Remove characters that are invalid in filenames."""
    name = name.strip()
    name = re.sub(r'[\\/:*?"<>|]', '', name)
    return name[:100] if name else "unknown"


def _cleanup_dir(dir_path: str):
    """Safely remove a directory and all its contents."""
    try:
        if os.path.isdir(dir_path):
            shutil.rmtree(dir_path, ignore_errors=True)
    except Exception as e:
        print("cleanup warning: " + str(e))


def get_pdf_path(album_id: str, output_dir: str) -> str:
    """
    Entry point called from Kotlin via Chaquopy.
    Returns a JSON string with the result.

    On success: {"success": true, "pdf_path": "/path/to/file.pdf"}
    On failure: {"success": false, "error": "...", "traceback": "..."}
    """
    try:
        pdf_path = download_album_as_pdf(album_id, output_dir)
        return json.dumps({"success": True, "pdf_path": pdf_path})
    except Exception as e:
        buf = io.StringIO()
        traceback.print_exc(file=buf)
        err_msg = "{}: {}".format(type(e).__name__, str(e))
        return json.dumps({
            "success": False,
            "error": err_msg,
            "traceback": buf.getvalue()
        })
