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
    results["customWebUIUpdate"] = graphql(
        "query { checkForWebUIUpdate { tag updateAvailable } }"
    )
    assert results["customWebUIUpdate"]["data"]["checkForWebUIUpdate"] == {
        "tag": "r3553", "updateAvailable": False
    }, results["customWebUIUpdate"]
    results["databaseSettings"] = graphql(
        "query { settings { databaseSchema hikariMaxPoolSize } }"
    )
    database_settings = results["databaseSettings"]["data"]["settings"]
    assert isinstance(database_settings["databaseSchema"], str), database_settings
    assert database_settings["hikariMaxPoolSize"] >= 1, database_settings
    results["schema"] = graphql(
        """query {
          komgaSettings { enabled apiUrl browserUrl credentialConfigured canManage autoLink showCounter }
        }"""
    )
    data = results["schema"]["data"]
    assert data["komgaSettings"] == {
        "enabled": False,
        "apiUrl": "",
        "browserUrl": "",
        "credentialConfigured": False,
        "canManage": True,
        "autoLink": False,
        "showCounter": True,
    }, data["komgaSettings"]
    # GraphQL Java limits repeated __Type.fields in one request. Keep each
    # introspection lookup separate, preserving the server's good-faith guard.
    results["roots"] = graphql("query { __schema { queryType { name } mutationType { name } } }")
    for root in ("queryType", "mutationType"):
        name = results["roots"]["data"]["__schema"][root]["name"]
        results[root] = graphql(
            "query { __type(name:" + json.dumps(name) + ") { fields { name } } }"
        )
    results["settingsType"] = graphql('query { __type(name:"KomgaSettings") { fields { name } } }')
    queries = {field["name"] for field in results["queryType"]["data"]["__type"]["fields"]}
    mutations = {field["name"] for field in results["mutationType"]["data"]["__type"]["fields"]}
    assert not {"users", "user", "userCodes"} & queries, queries
    assert not {
        "register", "updateUser", "createRegistrationCode", "redeemRegistrationCode",
        "createRecoveryCode", "redeemRecoveryCode", "revokeUserCode", "setPassword",
    } & mutations, mutations
    assert {"komgaSettings", "komgaSearch", "komgaManga", "komgaScanStatus", "komgaLibrarySummaries"} <= queries
    assert {
        "setKomgaSettings",
        "testKomgaConnection",
        "linkKomgaSeries",
        "unlinkKomgaSeries",
        "refreshKomgaManga",
        "scanKomgaLibrary",
    } <= mutations
    assert {field["name"] for field in results["settingsType"]["data"]["__type"]["fields"]} == {
        "enabled", "apiUrl", "browserUrl", "credentialConfigured", "canManage",
        "autoLink", "showCounter"
    }, results["settingsType"]

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
