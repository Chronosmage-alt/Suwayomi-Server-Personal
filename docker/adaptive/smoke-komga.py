"""Packaged-image checks against CI's disposable, loopback-only Suwayomi instance.

No Komga endpoint, credential, source fetch or production data is used.
"""

import json
import re
import urllib.request
from pathlib import Path


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


BASE = "http://127.0.0.1:14568"
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirects())


def request(path, payload=None):
    assert path.startswith("/") and not path.startswith("//"), path
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(
        BASE + path, data=data, headers={"Content-Type": "application/json"}
    )
    with HTTP.open(req, timeout=20) as response:
        return response.read()


def graphql(query, expected_error=None):
    result = json.loads(request("/api/graphql", {"query": query}))
    if expected_error:
        assert result.get("errors"), result
        assert any(
            error.get("path", [None])[0] == expected_error
            for error in result["errors"]
        ), result
    else:
        assert not result.get("errors"), result
    return result


def bundled_scripts(html):
    scripts = re.findall(r'<script\b[^>]*\bsrc="([^"]+)"', html)
    assets = [
        "/" + path[2:] if path.startswith("./assets/") else path
        for path in scripts
        if path.startswith(("/assets/", "./assets/"))
    ]
    assert assets, "No bundled WebUI JavaScript entry point"
    return assets


def main():
    results = {}
    results["schema"] = graphql(
        """query {
          __schema { queryType { fields { name } } mutationType { fields { name } } }
          __type(name: "KomgaSettings") { fields { name } }
          komgaSettings { enabled apiUrl browserUrl credentialConfigured canManage }
        }"""
    )
    data = results["schema"]["data"]
    assert data["komgaSettings"] == {
        "enabled": False,
        "apiUrl": "",
        "browserUrl": "",
        "credentialConfigured": False,
        "canManage": True,
    }, data["komgaSettings"]
    queries = {field["name"] for field in data["__schema"]["queryType"]["fields"]}
    mutations = {
        field["name"] for field in data["__schema"]["mutationType"]["fields"]
    }
    assert {"komgaSettings", "komgaSearch", "komgaManga"} <= queries
    assert {
        "setKomgaSettings",
        "testKomgaConnection",
        "linkKomgaSeries",
        "unlinkKomgaSeries",
        "refreshKomgaManga",
    } <= mutations
    assert {field["name"] for field in data["__type"]["fields"]} == {
        "enabled", "apiUrl", "browserUrl", "credentialConfigured", "canManage"
    }, data["__type"]

    results["save"] = graphql(
        'mutation { setKomgaSettings(enabled:false, apiUrl:"", browserUrl:"") '
        "{ enabled apiUrl browserUrl credentialConfigured } }"
    )
    assert results["save"]["data"]["setKomgaSettings"] == {
        "enabled": False, "apiUrl": "", "browserUrl": "", "credentialConfigured": False
    }
    results["test"] = graphql("mutation { testKomgaConnection { success code } }")
    assert results["test"]["data"]["testKomgaConnection"] == {
        "success": False, "code": "NOT_CONFIGURED"
    }
    results["search"] = graphql(
        'query { komgaSearch(query:"CI disposable fixture", page:0) '
        "{ code items { id } hasNextPage } }"
    )
    assert results["search"]["data"]["komgaSearch"] == {
        "code": "DISABLED", "items": [], "hasNextPage": False
    }

    # Exercise all remaining resolvers' missing-manga guards without any source
    # requests or remote Komga URL. The fresh CI database has no such manga.
    for operation, query in {
        "komgaManga": "query { komgaManga(mangaId:2147483647) { code } }",
        "linkKomgaSeries": 'mutation { linkKomgaSeries(mangaId:2147483647, seriesId:"ci") { code } }',
        "unlinkKomgaSeries": "mutation { unlinkKomgaSeries(mangaId:2147483647) { code } }",
        "refreshKomgaManga": "mutation { refreshKomgaManga(mangaId:2147483647) { code } }",
    }.items():
        results[operation] = graphql(query, expected_error=operation)

    html = request("/").decode()
    assets = bundled_scripts(html)
    for path in assets:
        assert request(path), f"Bundled WebUI asset could not be served: {path}"
    results["servedAssets"] = assets
    Path("komga-smoke-response.json").write_text(json.dumps(results, indent=2))
    print("Komga packaged GraphQL operations, default-user safety and served WebUI assets passed.")


if __name__ == "__main__":
    main()
