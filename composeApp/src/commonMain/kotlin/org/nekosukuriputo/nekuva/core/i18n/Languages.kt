package org.nekosukuriputo.nekuva.core.i18n

/**
 * BCP-47 language tags Nekuva ships translations for — mirrors the `composeResources/values-*` folders,
 * de-duplicated (legacy `in`/`iw` folders are covered by `id`/`he`; the base `values` catalog = `en`).
 * Empty tag (`""`) means "follow system" and is added separately by the picker.
 */
val SUPPORTED_LANGUAGE_TAGS: List<String> = listOf(
    "en", "ab", "ar", "arq", "arz", "as", "yue-Hant", "bci", "be", "bn", "ca", "ckb", "cs",
    "de", "el", "en-GB", "enm", "es", "et", "eu", "fa", "fi", "fil", "fr", "frp", "got", "gu",
    "he", "hi", "hr", "hu", "id", "it", "ja", "jv", "kk", "km", "ko", "lt", "lv", "lzh", "ml",
    "ms", "my", "nb-NO", "ne", "nl", "nn", "or", "pa", "pa-PK", "pl", "pt", "pt-BR", "ro", "ru",
    "si", "sr", "sv", "ta", "te", "th", "tr", "uk", "vi", "zh-CN", "zh-TW",
)
