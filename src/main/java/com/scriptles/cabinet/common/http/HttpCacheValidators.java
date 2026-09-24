package com.scriptles.cabinet.common.http;

public final class HttpCacheValidators {
    private HttpCacheValidators() {}

    public static String weakEtag(String resource, String version) {
        String safeVersion = version == null ? "unknown" : version.replace("\"", "");
        return "W/\"" + resource + ":" + safeVersion + "\"";
    }

    public static boolean matchesIfNoneMatch(String header, String currentEtag) {
        if (header == null || header.isBlank() || currentEtag == null) return false;
        String currentOpaqueTag = opaqueTag(currentEtag);
        for (String candidate : header.split(",")) {
            String value = candidate.trim();
            if ("*".equals(value)) return true;
            if (currentOpaqueTag.equals(opaqueTag(value))) return true;
        }
        return false;
    }

    private static String opaqueTag(String value) {
        String tag = value.trim();
        if (tag.startsWith("W/")) tag = tag.substring(2).trim();
        return tag;
    }
}
