package com.deepwater.platform;

/** Small URL string helpers, written without regular expressions. */
final class UrlText {
    private UrlText() {}

    /** "https://app.example/" and "https://app.example//" both become "https://app.example". */
    static String withoutTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') end--;
        return url.substring(0, end);
    }
}
