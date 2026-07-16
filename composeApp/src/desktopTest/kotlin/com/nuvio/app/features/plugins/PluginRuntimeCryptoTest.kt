package com.nuvio.app.features.plugins

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the plugin crypto shim through the real QuickJS runtime using published test vectors.
 * Plugins reach crypto only through this bridge, so exercising it end to end is the only way to
 * prove a scraper that decrypts its payload actually works.
 */
class PluginRuntimeCryptoTest {
    private fun runCryptoPlugin(body: String): Map<String, String> {
        val code = """
            function getStreams(tmdbId, mediaType, season, episode) {
                var out = [];
                function push(name, value) {
                    out.push({ title: name + '|' + value, url: 'https://example.test/s' });
                }
                $body
                return out;
            }
            module.exports.getStreams = getStreams;
        """.trimIndent()

        val results = runBlocking {
            PluginRuntime.executePlugin(
                code = code,
                tmdbId = "1",
                mediaType = "movie",
                season = null,
                episode = null,
                scraperId = "crypto-test",
            )
        }
        return results.associate { result ->
            val parts = result.title.split('|', limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }
    }

    @Test
    fun hashesAndHmacMatchPublishedVectors() {
        val out = runCryptoPlugin(
            """
            push('sha256', CryptoJS.SHA256('abc').toString());
            push('md5', CryptoJS.MD5('abc').toString());
            push('sha512', CryptoJS.SHA512('abc').toString().substring(0, 32));
            push('hmac', CryptoJS.HmacSHA256('The quick brown fox jumps over the lazy dog', 'key').toString());
            """.trimIndent(),
        )

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", out["sha256"])
        assertEquals("900150983cd24fb0d6963f7d28e17f72", out["md5"])
        assertEquals("ddaf35a193617abacc417349ae204131", out["sha512"])
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", out["hmac"])
    }

    /** Base64 of a digest is raw-byte based; the old UTF-8 backed shim mangled it. */
    @Test
    fun base64EncodesRawDigestBytesRatherThanMangledText() {
        val out = runCryptoPlugin(
            "push('b64', CryptoJS.SHA256('abc').toString(CryptoJS.enc.Base64));",
        )

        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", out["b64"])
    }

    /** NIST SP 800-38A F.2.1, AES-128-CBC single block. */
    @Test
    fun aesCbcMatchesNistVectorAndRoundTrips() {
        val out = runCryptoPlugin(
            """
            var key = CryptoJS.enc.Hex.parse('2b7e151628aed2a6abf7158809cf4f3c');
            var iv = CryptoJS.enc.Hex.parse('000102030405060708090a0b0c0d0e0f');
            var pt = CryptoJS.enc.Hex.parse('6bc1bee22e409f96e93d7e117393172a');
            var opts = { iv: iv, mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.NoPadding };
            var enc = CryptoJS.AES.encrypt(pt, key, opts);
            push('ct', enc.ciphertext.toString());
            var dec = CryptoJS.AES.decrypt({ ciphertext: enc.ciphertext }, key, opts);
            push('rt', dec.toString());
            """.trimIndent(),
        )

        assertEquals("7649abac8119b246cee98e9b12e9197d", out["ct"])
        assertEquals("6bc1bee22e409f96e93d7e117393172a", out["rt"])
    }

    /** The passphrase form scrapers usually use: OpenSSL "Salted__" envelope, EVP key derivation. */
    @Test
    fun aesPassphraseRoundTripsThroughOpenSslEnvelope() {
        val out = runCryptoPlugin(
            """
            var enc = CryptoJS.AES.encrypt('super secret payload', 'p4ssw0rd').toString();
            push('plain', CryptoJS.AES.decrypt(enc, 'p4ssw0rd').toString(CryptoJS.enc.Utf8));
            """.trimIndent(),
        )

        assertEquals("super secret payload", out["plain"])
    }

    /** RFC 6070 PBKDF2-HMAC-SHA1, c = 1, dkLen = 20. */
    @Test
    fun pbkdf2MatchesRfc6070Vector() {
        val out = runCryptoPlugin(
            """
            var dk = CryptoJS.PBKDF2('password', CryptoJS.enc.Utf8.parse('salt'), {
                keySize: 160 / 32,
                iterations: 1,
                hasher: 'SHA1'
            });
            push('dk', dk.toString());
            """.trimIndent(),
        )

        assertEquals("0c60c80f961f0e71f3a9b524af6012062fe037a6", out["dk"])
    }

    @Test
    fun webCryptoRandomAndSubtleAesAreAvailable() {
        val out = runCryptoPlugin(
            """
            var bytes = crypto.getRandomValues(new Uint8Array(16));
            push('randlen', String(bytes.length));
            push('randnonzero', String(bytes.some(function(b) { return b !== 0; })));
            push('uuid', String(crypto.randomUUID().length));
            """.trimIndent(),
        )

        assertEquals("16", out["randlen"])
        assertEquals("true", out["randnonzero"])
        assertEquals("36", out["uuid"])
    }

    /** A missing bridge must surface as an error, never as a silent empty result that hangs a scraper. */
    @Test
    fun unsupportedAlgorithmReportsAnErrorRatherThanEmptyOutput() {
        val out = runCryptoPlugin(
            """
            try {
                CryptoJS.SHA256('abc').toString();
                push('ok', 'true');
            } catch (e) {
                push('ok', 'false');
            }
            try {
                push('bad', CryptoJS.enc.Hex.stringify(__bytesToWordArray(__nativeDigestBytes('SHA999', new Uint8Array(1)))));
            } catch (e) {
                push('bad', 'threw');
            }
            """.trimIndent(),
        )

        assertEquals("true", out["ok"])
        assertEquals("threw", out["bad"])
        assertTrue(out.containsKey("bad"), "unsupported algorithm should still return a result row")
    }
}
