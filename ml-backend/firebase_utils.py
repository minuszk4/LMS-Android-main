import os
from functools import lru_cache
from typing import Any, Dict, Optional

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except ImportError:  # pragma: no cover - handled gracefully at runtime
    firebase_admin = None
    credentials = None
    firestore = None


REQUIRED_FIREBASE_ENV_VARS = (
    "FIREBASE_PROJECT_ID",
    "FIREBASE_PRIVATE_KEY_ID",
    "FIREBASE_PRIVATE_KEY",
    "FIREBASE_CLIENT_EMAIL",
    "FIREBASE_CLIENT_ID",
    "FIREBASE_CLIENT_X509_CERT_URL",
)


def is_firebase_configured() -> bool:
    if firebase_admin is None:
        return False
    return all(str(os.getenv(name, "")).strip() for name in REQUIRED_FIREBASE_ENV_VARS)


def build_service_account_from_env() -> Optional[Dict[str, Any]]:
    if not is_firebase_configured():
        return None

    private_key = os.getenv("FIREBASE_PRIVATE_KEY", "").replace("\\n", "\n")
    return {
        "type": "service_account",
        "project_id": os.getenv("FIREBASE_PROJECT_ID", "").strip(),
        "private_key_id": os.getenv("FIREBASE_PRIVATE_KEY_ID", "").strip(),
        "private_key": private_key,
        "client_email": os.getenv("FIREBASE_CLIENT_EMAIL", "").strip(),
        "client_id": os.getenv("FIREBASE_CLIENT_ID", "").strip(),
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_x509_cert_url": os.getenv("FIREBASE_CLIENT_X509_CERT_URL", "").strip(),
    }


@lru_cache(maxsize=1)
def get_firestore_client():
    if not is_firebase_configured():
        return None

    service_account = build_service_account_from_env()
    if service_account is None:
        return None

    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(service_account))

    return firestore.client()
