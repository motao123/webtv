package com.github.catvod.utils;

import android.text.TextUtils;

import androidx.annotation.Nullable;

public final class UriUtil {

    private static final int INDEX_COUNT = 4;
    private static final int SCHEME_COLON = 0;
    private static final int PATH = 1;
    private static final int QUERY = 2;
    private static final int FRAGMENT = 3;

    public static String resolve(@Nullable String baseUri, @Nullable String referenceUri) {
        StringBuilder uri = new StringBuilder();
        baseUri = baseUri == null ? "" : baseUri;
        referenceUri = referenceUri == null ? "" : referenceUri;
        int[] refIndices = getUriIndices(referenceUri);
        if (refIndices[SCHEME_COLON] != -1) {
            uri.append(referenceUri);
            removeDotSegments(uri, refIndices[PATH], refIndices[QUERY]);
            return uri.toString();
        }
        int[] baseIndices = getUriIndices(baseUri);
        if (refIndices[FRAGMENT] == 0) {
            return uri.append(baseUri, 0, baseIndices[FRAGMENT]).append(referenceUri).toString();
        }
        if (refIndices[QUERY] == 0) {
            return uri.append(baseUri, 0, baseIndices[QUERY]).append(referenceUri).toString();
        }
        if (refIndices[PATH] != 0) {
            int baseLimit = baseIndices[SCHEME_COLON] + 1;
            uri.append(baseUri, 0, baseLimit).append(referenceUri);
            return removeDotSegments(uri, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY]);
        }
        if (referenceUri.charAt(refIndices[PATH]) == '/') {
            uri.append(baseUri, 0, baseIndices[PATH]).append(referenceUri);
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY]);
        }
        if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH] && baseIndices[PATH] == baseIndices[QUERY]) {
            uri.append(baseUri, 0, baseIndices[PATH]).append('/').append(referenceUri);
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY] + 1);
        } else {
            int lastSlashIndex = baseUri.lastIndexOf('/', baseIndices[QUERY] - 1);
            int baseLimit = lastSlashIndex == -1 ? baseIndices[PATH] : lastSlashIndex + 1;
            uri.append(baseUri, 0, baseLimit).append(referenceUri);
            return removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY]);
        }
    }

    private static String removeDotSegments(StringBuilder uri, int offset, int limit) {
        if (offset >= limit) {
            return uri.toString();
        }
        if (uri.charAt(offset) == '/') {
            offset++;
        }
        int segmentStart = offset;
        int i = offset;
        while (i <= limit) {
            int nextSegmentStart;
            if (i == limit) {
                nextSegmentStart = i;
            } else if (uri.charAt(i) == '/') {
                nextSegmentStart = i + 1;
            } else {
                i++;
                continue;
            }
            if (i == segmentStart + 1 && uri.charAt(segmentStart) == '.') {
                uri.delete(segmentStart, nextSegmentStart);
                limit -= nextSegmentStart - segmentStart;
                i = segmentStart;
            } else if (i == segmentStart + 2 && uri.charAt(segmentStart) == '.' && uri.charAt(segmentStart + 1) == '.') {
                int prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1;
                int removeFrom = Math.max(prevSegmentStart, offset);
                uri.delete(removeFrom, nextSegmentStart);
                limit -= nextSegmentStart - removeFrom;
                segmentStart = prevSegmentStart;
                i = prevSegmentStart;
            } else {
                i++;
                segmentStart = i;
            }
        }
        return uri.toString();
    }

    private static int[] getUriIndices(String uriString) {
        int[] indices = new int[INDEX_COUNT];
        if (TextUtils.isEmpty(uriString)) {
            indices[SCHEME_COLON] = -1;
            return indices;
        }
        int length = uriString.length();
        int fragmentIndex = uriString.indexOf('#');
        if (fragmentIndex == -1) {
            fragmentIndex = length;
        }
        int queryIndex = uriString.indexOf('?');
        if (queryIndex == -1 || queryIndex > fragmentIndex) {
            queryIndex = fragmentIndex;
        }
        int schemeIndexLimit = uriString.indexOf('/');
        if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
            schemeIndexLimit = queryIndex;
        }
        int schemeIndex = uriString.indexOf(':');
        if (schemeIndex > schemeIndexLimit) {
            schemeIndex = -1;
        }
        boolean hasAuthority = schemeIndex + 2 < queryIndex && uriString.length() > schemeIndex + 2 && uriString.charAt(schemeIndex + 1) == '/' && uriString.charAt(schemeIndex + 2) == '/';
        int pathIndex;
        if (hasAuthority) {
            pathIndex = uriString.indexOf('/', schemeIndex + 3);
            if (pathIndex == -1 || pathIndex > queryIndex) {
                pathIndex = queryIndex;
            }
        } else {
            pathIndex = schemeIndex + 1;
        }
        indices[SCHEME_COLON] = schemeIndex;
        indices[PATH] = pathIndex;
        indices[QUERY] = queryIndex;
        indices[FRAGMENT] = fragmentIndex;
        return indices;
    }
}
