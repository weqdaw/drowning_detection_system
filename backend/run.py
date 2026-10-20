import os
import sys

# Ensure backend directory is first in sys.path and current working directory
backend_dir = os.path.dirname(os.path.abspath(__file__))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)
os.chdir(backend_dir)

import uvicorn

if __name__ == "__main__":
    print("Starting Video AI Analysis Platform Backend...")
    print("Access REST APIs at http://127.0.0.1:8000/docs")
    print("Access Web Console at http://127.0.0.1:8000/platform/")

    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=False)
