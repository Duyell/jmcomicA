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


# ---- Mock curl_cffi & async modules BEFORE any jmcomic import ----
# curl_cffi has native .pyd/.so incompatible with Android.
# jmcomic 2.7.2 added async modules that import curl_cffi at module level.
# We mock them here so the entire import chain succeeds cleanly.
import types as _types
import importlib.abc as _abc
import importlib.machinery as _mach

# 1) Pre-seed sys.modules with fake curl_cffi packages
_curl_cffi = _types.ModuleType("curl_cffi")
_curl_cffi.__path__ = []
_curl_cffi_requests = _types.ModuleType("curl_cffi.requests")
class _DummyAsyncSession: pass
_curl_cffi_requests.AsyncSession = _DummyAsyncSession
_curl_cffi.requests = _curl_cffi_requests
sys.modules["curl_cffi"] = _curl_cffi
sys.modules["curl_cffi.requests"] = _curl_cffi_requests

# 2) MetaPathFinder: mock jmcomic's async sub-modules so __init__.py's
#    `from .jm_async_client import AsyncJmApiClient` etc. don't crash.
_MOCKED_JM_MODULES = {
    "jmcomic.jm_async_client",
    "jmcomic.jm_async_downloader",
}

class _JmAsyncModuleLoader(_abc.Loader):
    """Loader that creates an empty mock module."""
    def create_module(self, spec):
        mod = _types.ModuleType(spec.name)
        # Provide the class that __init__.py expects
        if spec.name == "jmcomic.jm_async_client":
            class _MockAsyncJmApiClient: pass
            mod.AsyncJmApiClient = _MockAsyncJmApiClient
        if spec.name == "jmcomic.jm_async_downloader":
            class _MockJmAsyncDownloader: pass
            mod.JmAsyncDownloader = _MockJmAsyncDownloader
        return mod

    def exec_module(self, module):
        pass

class _JmAsyncFinder(_abc.MetaPathFinder):
    def find_spec(self, fullname, path=None, target=None):
        if fullname in _MOCKED_JM_MODULES:
            spec = _mach.ModuleSpec(fullname, _JmAsyncModuleLoader(), is_package=False)
            return spec
        return None

sys.meta_path.insert(0, _JmAsyncFinder())
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


def _write_progress(output_dir, stage, current=0, total=0, message=""):
    """Write a progress.json file for the Kotlin side to poll."""
    try:
        path = os.path.join(output_dir, "progress.json")
        with open(path, 'w') as f:
            json.dump({
                "stage": stage,
                "current": current,
                "total": total,
                "message": message,
            }, f)
    except Exception:
        pass


def _detect_emulator_proxy():
    """
    Auto-detect a local HTTP proxy on common ports.
    Tries 10.0.2.2 (Android emulator host) and 127.0.0.1 (real device).
    Returns proxy URL or empty string.
    """
    import socket
    for host in ('10.0.2.2', '127.0.0.1'):
        for port in (7897, 7890, 10809, 10808):
            try:
                s = socket.socket()
                s.settimeout(0.3)
                s.connect((host, port))
                s.close()
                return 'http://{}:{}'.format(host, port)
            except Exception:
                pass
    return ''


def _translate_error(exc: Exception) -> str:
    """将 Python 异常翻译为简短中文消息（10字以内）。"""
    exc_type = type(exc).__name__
    exc_msg = str(exc) if str(exc) else ""

    # 网络 / 连接类
    if exc_type in ('ConnectionError', 'MaxRetryError', 'NewConnectionError',
                    'ProtocolError', 'RemoteDisconnected', 'RequestRetryAllFailException'):
        return '网络连接失败'

    if 'Timeout' in exc_type or 'timeout' in exc_msg.lower():
        return '连接超时'

    if 'ProxyError' in exc_type:
        return '代理连接失败'

    if 'SSLError' in exc_type:
        return '证书验证失败'

    # JM 业务类
    if 'MissingAlbumPhotoException' in exc_type or 'MissingAlbumPhoto' in exc_type:
        return '漫画不存在'

    if 'JsonDataException' in exc_type or 'JmException' in exc_type:
        return '服务器异常'

    if 'AssertionError' in exc_type:
        return '请求被拒'

    # 文件 / 本地类
    if exc_type == 'FileNotFoundError':
        return '文件未找到'

    if 'Permission' in exc_type:
        return '权限不足'

    # 导入 / 模块类
    if exc_type in ('ImportError', 'ModuleNotFoundError'):
        return '模块加载失败'

    # 数据解析类
    if 'JSONDecodeError' in exc_type or 'YAMLError' in exc_type:
        return '数据解析失败'

    # RuntimeError
    if exc_type == 'RuntimeError':
        if 'no valid images' in exc_msg:
            return '无有效图片'
        if 'BitmapFactory' in exc_msg:
            return '图片解码失败'

    # 兜底
    return '下载失败'


# 官方域名自动探测开关。开启后，当用户未填「备用域名」时，会依次尝试 JM 官方
# 永久跳转链接与官方发布页来自动获取最新可用域名，让你无需手动知道任何域名。
# 这两个官方源与库内置的字节云(newsvr)自动更新是*不同*的域名，在部分地区
# 可能绕开封锁，从而让「无需手动填域名也能用」成为可能。
OFFICIAL_DOMAIN_DISCOVERY = True


def _host_of(url: str):
    """从 URL 中提取 host（去掉端口），失败返回空串。"""
    try:
        from urllib.parse import urlparse
        netloc = urlparse(url).netloc
        return netloc.split(':')[0]
    except Exception:
        return ''


def _discover_jm_domains(proxy: str):
    """
    自动探测可用的 JM 域名（零用户知识）。

    探测顺序:
      1) JM 官方永久跳转链接  jm365.work/3YeBdF -> 当前可用域名
      2) JM 官方发布页        jmcomicgo.org       -> 列出全部域名

    返回去重后的域名列表；两个官方源都不可达时返回 []（调用方会退回
    库内置默认域名 + 字节云自动更新，保留原有行为）。
    """
    if not OFFICIAL_DOMAIN_DISCOVERY:
        return []

    try:
        from jmcomic.jm_config import JmModuleConfig
    except Exception as e:
        print("JM_DISCOVER: cannot import JmModuleConfig:", e, file=sys.stderr)
        return []

    import socket
    found = []
    old_to = socket.getdefaulttimeout()
    socket.setdefaulttimeout(10)  # 限时，避免网络不可达时卡死
    try:
        # 1) 官方永久跳转链接：jm365.work/3YeBdF -> 当前可用域名
        try:
            url = JmModuleConfig.get_html_url()
            host = _host_of(url)
            if host and 'jm365' not in host.lower():
                found.append(host)
                print("JM_DISCOVER: via permanent-link ->", host, file=sys.stderr)
        except Exception as e:
            print("JM_DISCOVER: permanent-link failed:", repr(e), file=sys.stderr)

        # 2) 官方发布页：jmcomicgo.org 列出全部域名
        if not found:
            try:
                doms = JmModuleConfig.get_html_domain_all()
                for d in doms:
                    d = (d or '').strip()
                    if d and not d.lower().startswith('jm365'):
                        found.append(d)
                print("JM_DISCOVER: via pub-page ->", found, file=sys.stderr)
            except Exception as e:
                print("JM_DISCOVER: pub-page failed:", repr(e), file=sys.stderr)
    finally:
        socket.setdefaulttimeout(old_to)

    # 去重保序
    seen = set()
    result = []
    for d in found:
        dl = d.lower()
        if dl not in seen:
            seen.add(dl)
            result.append(d)
    return result


def download_album_as_pdf(album_id: str, output_dir: str,
                          proxy_url: str = "") -> str:
    """
    Download a JM comic album and convert all images to a single PDF.
    Returns the path to the generated PDF file.

    :param proxy_url:  显式代理地址，如 http://127.0.0.1:7890 。
                        为空时退回 emulator 自动探测；仍为空则不使用代理(直连)。
    """
    os.makedirs(output_dir, exist_ok=True)

    try:
        import jmcomic
        from jmcomic.jm_client_interface import JmImageResp
    except Exception as _import_err:
        # Dump FULL details so we can see the real error in logcat
        import traceback as _tb
        _detail = _tb.format_exc()
        _crash_log = os.path.join(output_dir, "crash.log")
        try:
            with open(_crash_log, 'w', encoding='utf-8') as _f:
                _f.write("=== JMCOMIC IMPORT FAILED ===\n")
                _f.write(_detail)
                _f.write("\n=== sys.modules[curl_cffi] ===\n")
                _f.write(str(sys.modules.get("curl_cffi", "NOT FOUND")))
        except Exception:
            pass
        # Also print to stderr for logcat visibility
        print("CRITICAL: import jmcomic failed:\n" + _detail, file=sys.stderr)
        raise

    _image_meta = {}

    _orig_transfer_to = JmImageResp.transfer_to

    def _patched_transfer_to(self, path, scramble_id, decode_image=True, img_url=None):
        with open(path, 'wb') as f:
            f.write(self.content)
        if scramble_id is not None and img_url is not None:
            from jmcomic.jm_toolkit import JmImageTool
            _image_meta[path] = JmImageTool.get_num_by_url(scramble_id, img_url)

    JmImageResp.transfer_to = _patched_transfer_to

    # 代理优先级：用户显式代理 > emulator 自动探测 > 不使用代理(直连)
    proxy = (proxy_url or '').strip()
    if not proxy:
        proxy = _detect_emulator_proxy()
    proxy_yaml = ''
    if proxy:
        proxy_yaml = """
      proxies:
        http: {proxy}
        https: {proxy}""".format(proxy=proxy)

    # 若设置了代理，让域名自动探测也走代理（官方域名源可能也需要代理才能连通）
    _proxy_env_saved = {}
    if proxy:
        for _k in ('http_proxy', 'https_proxy'):
            _proxy_env_saved[_k] = os.environ.get(_k)
            os.environ[_k] = proxy

    # 域名解析：自动探测官方域名（jm365.work 永久跳转 / jmcomicgo.org 发布页）
    # 作为唯一机制；若两个官方源都不可达，则退回库内置默认域名+字节云自动更新。
    domain_list = []
    discover_src = 'default'
    if not domain_list:
        try:
            domain_list = _discover_jm_domains(proxy)
            discover_src = 'auto' if domain_list else 'default'
        finally:
            # 还原代理环境变量，避免影响后续下载逻辑
            for _k, _v in _proxy_env_saved.items():
                if _v is None:
                    os.environ.pop(_k, None)
                else:
                    os.environ[_k] = _v

    domain_yaml = ''
    if domain_list:
        lines = "\n".join("    - {}".format(d) for d in domain_list)
        domain_yaml = "\n  domain:\n{}".format(lines)

    print("JM_BRIDGE_CONFIG: proxy={!r} domain_source={} domains={}".format(
        proxy, discover_src, domain_list), file=sys.stderr)

    option_text = """
client:
  impl: api{domain_yaml}
  postman:
    type: requests
    meta_data:
      timeout: 30{proxy_yaml}

dir_rule:
  rule: Bd_Aid_{{Pindextitle}}
  base_dir: {output_dir}/downloads

plugins:
  after_album: []
""".format(output_dir=output_dir.replace('\\', '/'),
           proxy_yaml=proxy_yaml,
           domain_yaml=domain_yaml)

    option = jmcomic.create_option_by_str(option_text)
    album_id = str(album_id).strip()

    download_base = os.path.join(output_dir, "downloads")

    try:
        _write_progress(output_dir, "connecting", 0, 0, "获取漫画信息...")

        album, downloader = jmcomic.download_album(album_id, option)
        downloader.raise_if_has_exception()

        _write_progress(output_dir, "unscramble", 0, len(_image_meta), "解扰图片...")
        _unscramble_via_android(_image_meta, output_dir)

        # Collect images via album object hierarchy (album → photos → images),
        # which preserves chapter order even for multi-chapter comics.
        all_image_paths = _collect_images_from_album(album, download_base)

        if not all_image_paths:
            raise FileNotFoundError(
                "No images found for album_id={} in {}".format(album_id, download_base)
            )

        safe_title = _sanitize_filename(album.title)
        pdf_filename = "[JM{}] {}.pdf".format(album_id, safe_title)
        pdf_path = os.path.join(output_dir, pdf_filename)

        _images_to_pdf(all_image_paths, pdf_path, output_dir)

        if not os.path.exists(pdf_path):
            raise FileNotFoundError("PDF not generated: " + pdf_path)

        _write_progress(output_dir, "done", 0, 0, "")
        return pdf_path

    finally:
        try:
            JmImageResp.transfer_to = _orig_transfer_to
        except Exception:
            pass
        # Delete progress file
        try:
            os.remove(os.path.join(output_dir, "progress.json"))
        except Exception:
            pass
        _cleanup_dir(download_base)


def _unscramble_via_android(image_meta: dict, output_dir: str):
    """
    Unscramble WebP images using Android's Bitmap + Canvas API.
    Reorders horizontal strips in-place, saves as JPEG.
    """
    from android.graphics import (
        BitmapFactory, Bitmap, Canvas, Paint, Rect,
    )

    total = len(image_meta)
    for idx, (filepath, num) in enumerate(image_meta.items()):
        if not os.path.exists(filepath):
            continue

        _write_progress(output_dir, "unscramble", idx + 1, total,
                        "解扰 {}/{}".format(idx + 1, total))

        bitmap = BitmapFactory.decodeFile(filepath)
        if bitmap is None:
            print("unscramble_fail: decode failed for {}".format(filepath))
            continue

        w = bitmap.getWidth()
        h = bitmap.getHeight()

        if num <= 1:
            _save_bitmap_as_jpeg(bitmap, filepath)
            continue

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


def _collect_images_from_album(album, download_base: str) -> list:
    """
    Collect all downloaded image paths from the album object, in chapter order.

    Iterates album → photos (chapters) → images, using each image's
    ``save_path`` (set by jmcomic's downloader). Images are ordered by
    chapter then by filename within each chapter.

    Returns a flat list of absolute file paths ready for PDF composition.
    """
    extensions = {'.jpg', '.jpeg', '.png', '.webp', '.bmp'}
    all_paths = []
    for photo in album:
        for image in photo:
            path = getattr(image, 'save_path', None)
            if path and os.path.isfile(path):
                all_paths.append(path)
            else:
                # Fallback: search download_base for matching filename
                fname = getattr(image, 'filename', '') or getattr(image, 'img_file_name', '')
                if fname:
                    for root, _dirs, files in os.walk(download_base):
                        if fname in files:
                            all_paths.append(os.path.join(root, fname))
                            break

    if not all_paths:
        # Last resort: walk the download tree (old behaviour)
        for root, dirs, files in os.walk(download_base):
            dirs.sort()
            for f in sorted(files):
                if os.path.splitext(f)[1].lower() in extensions:
                    all_paths.append(os.path.join(root, f))

    return all_paths


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


def _images_to_pdf(image_paths: list, output_pdf: str, output_dir: str):
    """
    Convert a list of image paths into a single PDF.
    Uses Pillow for JPEG/PNG, and Android BitmapFactory for WebP.
    """
    from PIL import Image, features

    webp_supported = features.check("webp")
    total = len(image_paths)
    image_objects = []
    skipped = []
    android_temp_files = []

    for idx, path in enumerate(image_paths):
        _write_progress(output_dir, "pdf", idx + 1, total,
                        "合成PDF {}/{}".format(idx + 1, total))

        try:
            img = Image.open(path)
            img.load()
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


def get_pdf_path(album_id: str, output_dir: str,
                 proxy_url: str = "") -> str:
    """
    Entry point called from Kotlin via Chaquopy.
    Returns a JSON string with the result.

    :param proxy_url:  显式代理地址(可选)。为空时退回 emulator 探测/直连。

    On success: {"success": true, "pdf_path": "/path/to/file.pdf"}
    On failure: {"success": false, "error": "...", "user_message": "中文提示", "traceback": "..."}
    """
    try:
        pdf_path = download_album_as_pdf(album_id, output_dir,
                                         proxy_url=proxy_url)
        return json.dumps({"success": True, "pdf_path": pdf_path})
    except Exception as e:
        # 构建技术错误信息（仅用于日志）
        err_type = type(e).__name__
        err_msg = str(e) if str(e) else "(无详细信息)"

        # 遍历异常链
        cause = e.__cause__
        chain = ""
        while cause is not None:
            chain += "\n由以下引发: {}: {}".format(type(cause).__name__, cause)
            cause = cause.__cause__

        buf = io.StringIO()
        traceback.print_exc(file=buf)
        full_tb = buf.getvalue()

        # 技术错误摘要
        formatted = "[{}] {}{}\n\n{}".format(err_type, err_msg, chain, full_tb)
        print("JM_BRIDGE_ERROR: " + formatted, file=sys.stderr)

        # 中文用户友好消息
        user_message = _translate_error(e)

        return json.dumps({
            "success": False,
            "error": "[{}] {}{}".format(err_type, err_msg, chain),
            "user_message": user_message,
            "traceback": full_tb,
        })
