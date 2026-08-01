from pathlib import Path
from urllib.parse import ParseResult, urlparse
import os


class UrlManager:
    DASHBOARD_SECTION_HEADER = (
        "# URLs adicionadas pelo dashboard"
    )

    def __init__( self,   urls_file: Path, supported_stores: dict[str, str],) -> None:
        self.urls_file = urls_file
        self.supported_stores = supported_stores

    def add_url(
            self,
            raw_url: str,
    ) -> tuple[str, str]:
        cleaned_url, store_name = (
            self.validate_url(raw_url)
        )

        normalized_new_url = (
            self._normalize_for_comparison(
                cleaned_url
            )
        )

        normalized_existing_urls = {
            self._normalize_for_comparison(url)
            for url in self.list_active_urls()
        }

        if (
                normalized_new_url
                in normalized_existing_urls
        ):
            raise ValueError(
                "Esta URL já está cadastrada."
            )

        self._append_url(cleaned_url)

        return cleaned_url, store_name

    def validate_url(
            self,
            raw_url: str,
    ) -> tuple[str, str]:
        url = raw_url.strip()

        if not url:
            raise ValueError(
                "Informe uma URL antes de adicionar."
            )

        if len(url) > 2048:
            raise ValueError(
                "A URL ultrapassa o limite "
                "de 2048 caracteres."
            )

        try:
            parsed = urlparse(url)
            port = parsed.port
        except ValueError as exception:
            raise ValueError(
                "A URL possui uma porta inválida."
            ) from exception

        if parsed.scheme.lower() not in {
            "http",
            "https",
        }:
            raise ValueError(
                "A URL deve começar com http:// ou https://."
            )

        if not parsed.hostname:
            raise ValueError(
                "A URL não possui um domínio válido."
            )

        if parsed.username or parsed.password:
            raise ValueError(
                "URLs com usuário ou senha "
                "não são permitidas."
            )

        if port not in {None, 80, 443}:
            raise ValueError(
                "A URL utiliza uma porta "
                "não permitida."
            )

        store_name = self._identify_store(
            parsed.hostname
        )

        if store_name is None:
            supported_names = ", ".join(
                self.supported_stores.values()
            )

            raise ValueError(
                "Loja não suportada. "
                f"Lojas disponíveis: {supported_names}."
            )

        cleaned_url = parsed._replace(
            fragment=""
        ).geturl()

        return cleaned_url, store_name

    def list_active_urls(self) -> list[str]:
        if not self.urls_file.exists():
            return []

        content = self.urls_file.read_text(
            encoding="utf-8"
        )

        return [
            line.strip()
            for line in content.splitlines()
            if line.strip()
               and not line.lstrip().startswith("#")
        ]

    def _identify_store(
            self,
            hostname: str,
    ) -> str | None:
        normalized_host = (
            hostname.lower()
            .strip()
            .rstrip(".")
        )

        for (
                domain,
                store_name,
        ) in self.supported_stores.items():
            if (
                    normalized_host == domain
                    or normalized_host.endswith(
                f".{domain}"
            )
            ):
                return store_name

        return None

    def _normalize_for_comparison(
            self,
            url: str,
    ) -> str:
        parsed = urlparse(url.strip())

        scheme = parsed.scheme.lower()
        hostname = (
            parsed.hostname.lower()
            if parsed.hostname
            else ""
        )

        path = parsed.path.rstrip("/")

        normalized = (
            f"{scheme}://{hostname}{path}"
        )

        if parsed.query:
            normalized += f"?{parsed.query}"

        return normalized

    def _append_url(
            self,
            url: str,
    ) -> None:
        self.urls_file.parent.mkdir(
            parents=True,
            exist_ok=True,
        )

        current_content = ""

        if self.urls_file.exists():
            current_content = (
                self.urls_file.read_text(
                    encoding="utf-8"
                )
            )

        with self.urls_file.open(
                "a",
                encoding="utf-8",
        ) as file:
            if (
                    current_content
                    and not current_content.endswith(
                "\n"
            )
            ):
                file.write("\n")

            if (
                    self.DASHBOARD_SECTION_HEADER
                    not in current_content
            ):
                if current_content.strip():
                    file.write("\n")

                file.write(
                    self.DASHBOARD_SECTION_HEADER
                    + "\n"
                )

            file.write(url + "\n")
            file.flush()
            os.fsync(file.fileno())
