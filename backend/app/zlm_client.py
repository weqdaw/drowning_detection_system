import requests
import logging
from urllib.parse import urljoin
from .config import settings

logger = logging.getLogger("ZLMClient")

class ZLMClient:
    def __init__(self):
        self.api_url = settings.ZLM_API_URL
        self.secret = settings.ZLM_SECRET

    def _request(self, endpoint: str, params: dict = None) -> dict:
        if params is None:
            params = {}
        params["secret"] = self.secret
        url = urljoin(self.api_url, endpoint)
        try:
            response = requests.get(url, params=params, timeout=5)
            if response.status_code == 200:
                result = response.json()
                if result.get("code") == 0:
                    return result
                else:
                    logger.warning(f"ZLM API Error: {result.get('msg')} (code: {result.get('code')})")
                    return {"success": False, "msg": result.get("msg"), "code": result.get("code")}
            else:
                logger.error(f"ZLM server returned status code {response.status_code}")
                return {"success": False, "msg": f"HTTP status {response.status_code}"}
        except Exception as e:
            logger.error(f"Failed to connect to ZLMediaKit API at {url}: {e}")
            return {"success": False, "msg": str(e)}

    def is_alive(self) -> bool:
        """Check if ZLMediaKit is running and secret is correct"""
        result = self._request("/index/api/getApiList")
        return "code" in result and result["code"] == 0

    def add_stream_proxy(self, vhost: str, app: str, stream_id: str, url: str, rtp_type: int = 0) -> dict:
        """
        Add an RTSP pulling proxy to ZLMediaKit.
        rtp_type: 0 for TCP, 1 for UDP. TCP is recommended for RTSP streaming.
        """
        params = {
            "vhost": vhost,
            "app": app,
            "stream": stream_id,
            "url": url,
            "rtp_type": rtp_type,
            "enable_hls": 1,
            "enable_mp4": 0,
            "enable_rtsp": 1,
            "enable_rtmp": 1,
            "enable_ts": 0,
            "enable_fmp4": 1
        }
        return self._request("/index/api/addStreamProxy", params)

    def del_stream_proxy(self, key: str) -> dict:
        """
        Delete a stream proxy using the key returned by addStreamProxy.
        """
        params = {"key": key}
        return self._request("/index/api/delStreamProxy", params)

    def close_streams(self, schema: str, vhost: str, app: str, stream_id: str, force: int = 1) -> dict:
        """
        Force close a stream.
        """
        params = {
            "schema": schema,
            "vhost": vhost,
            "app": app,
            "stream": stream_id,
            "force": force
        }
        return self._request("/index/api/close_streams", params)

    def get_media_list(self, schema: str = "", vhost: str = "", app: str = "", stream_id: str = "") -> list:
        """
        Get the list of active streams on ZLMediaKit.
        """
        params = {}
        if schema: params["schema"] = schema
        if vhost: params["vhost"] = vhost
        if app: params["app"] = app
        if stream_id: params["stream"] = stream_id

        result = self._request("/index/api/getMediaList", params)
        if result.get("code") == 0 and "data" in result:
            return result["data"]
        return []

zlm_client = ZLMClient()
