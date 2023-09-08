/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.util.NetUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;

import static java.util.Arrays.asList;

/**
 * Constants for SSL packets.
 */
final class SslUtils {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SslUtils.class);

    // See https://tools.ietf.org/html/rfc8446#appendix-B.4
    static final Set<String> TLSV13_CIPHERS = Collections.unmodifiableSet(new LinkedHashSet<String>(
            asList("TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256",
                          "TLS_AES_128_GCM_SHA256", "TLS_AES_128_CCM_8_SHA256",
                          "TLS_AES_128_CCM_SHA256")));

    /**
     * GMSSL Protocol Version
     */
    static final int GMSSL_PROTOCOL_VERSION = 0x101;

    static final String INVALID_CIPHER = "SSL_NULL_WITH_NULL_NULL";

    /**
     * change cipher spec
     */
    static final int SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC = 20;

    /**
     * alert
     */
    static final int SSL_CONTENT_TYPE_ALERT = 21;

    /**
     * handshake
     */
    static final int SSL_CONTENT_TYPE_HANDSHAKE = 22;

    /**
     * application data
     */
    static final int SSL_CONTENT_TYPE_APPLICATION_DATA = 23;

    /**
     * HeartBeat Extension
     */
    static final int SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT = 24;

    /**
     * the length of the ssl record header (in bytes)
     */
    static final int SSL_RECORD_HEADER_LENGTH = 5;

    /**
     * Not enough data in buffer to parse the record length
     */
    static final int NOT_ENOUGH_DATA = -1;

    /**
     * data is not encrypted
     */
    static final int NOT_ENCRYPTED = -2;

    static final String[] DEFAULT_CIPHER_SUITES;
    static final String[] DEFAULT_TLSV13_CIPHER_SUITES;
    static final String[] TLSV13_CIPHER_SUITES = { "TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384" };

    private static final boolean TLSV1_3_JDK_SUPPORTED;
    private static final boolean TLSV1_3_JDK_DEFAULT_ENABLED;

    static {
        TLSV1_3_JDK_SUPPORTED = isTLSv13SupportedByJDK0(null);
        TLSV1_3_JDK_DEFAULT_ENABLED = isTLSv13EnabledByJDK0(null);
        if (TLSV1_3_JDK_SUPPORTED) {
            DEFAULT_TLSV13_CIPHER_SUITES = TLSV13_CIPHER_SUITES;
        } else {
            DEFAULT_TLSV13_CIPHER_SUITES = EmptyArrays.EMPTY_STRINGS;
        }

        Set<String> defaultCiphers = new LinkedHashSet<String>();
        // GCM (Galois/Counter Mode) requires JDK 8.
        defaultCiphers.add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
        defaultCiphers.add("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256");
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA");
        // AES256 requires JCE unlimited strength jurisdiction policy files.
        defaultCiphers.add("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA");
        // GCM (Galois/Counter Mode) requires JDK 8.
        defaultCiphers.add("TLS_RSA_WITH_AES_128_GCM_SHA256");
        defaultCiphers.add("TLS_RSA_WITH_AES_128_CBC_SHA");
        // AES256 requires JCE unlimited strength jurisdiction policy files.
        defaultCiphers.add("TLS_RSA_WITH_AES_256_CBC_SHA");

        Collections.addAll(defaultCiphers, DEFAULT_TLSV13_CIPHER_SUITES);

        DEFAULT_CIPHER_SUITES = defaultCiphers.toArray(EmptyArrays.EMPTY_STRINGS);
    }

    /**
     * Returns {@code true} if the JDK itself supports TLSv1.3, {@code false} otherwise.
     */
    static boolean isTLSv13SupportedByJDK(Provider provider) {
        if (provider == null) {
            return TLSV1_3_JDK_SUPPORTED;
        }
        return isTLSv13SupportedByJDK0(provider);
    }

    private static boolean isTLSv13SupportedByJDK0(Provider provider) {
        try {
            return arrayContains(newInitContext(provider)
                    .getSupportedSSLParameters().getProtocols(), SslProtocols.TLS_v1_3);
        } catch (Throwable cause) {
            logger.debug("Unable to detect if JDK SSLEngine with provider {} supports TLSv1.3, assuming no",
                    provider, cause);
            return false;
        }
    }

    /**
     * Returns {@code true} if the JDK itself supports TLSv1.3 and enabled it by default, {@code false} otherwise.
     */
    static boolean isTLSv13EnabledByJDK(Provider provider) {
        if (provider == null) {
            return TLSV1_3_JDK_DEFAULT_ENABLED;
        }
        return isTLSv13EnabledByJDK0(provider);
    }

    private static boolean isTLSv13EnabledByJDK0(Provider provider) {
        try {
            return arrayContains(newInitContext(provider)
                    .getDefaultSSLParameters().getProtocols(), SslProtocols.TLS_v1_3);
        } catch (Throwable cause) {
            logger.debug("Unable to detect if JDK SSLEngine with provider {} enables TLSv1.3 by default," +
                    " assuming no", provider, cause);
            return false;
        }
    }

    private static SSLContext newInitContext(Provider provider)
            throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext context;
        if (provider == null) {
            context = SSLContext.getInstance("TLS");
        } else {
            context = SSLContext.getInstance("TLS", provider);
        }
        context.init(null, new TrustManager[0], null);
        return context;
    }

    static SSLContext getSSLContext(String provider)
            throws NoSuchAlgorithmException, KeyManagementException, NoSuchProviderException {
        final SSLContext context;
        if (StringUtil.isNullOrEmpty(provider)) {
            context = SSLContext.getInstance(getTlsVersion());
        } else {
            context = SSLContext.getInstance(getTlsVersion(), provider);
        }
        context.init(null, new TrustManager[0], null);
        return context;
    }

    private static String getTlsVersion() {
        return TLSV1_3_JDK_SUPPORTED ? SslProtocols.TLS_v1_3 : SslProtocols.TLS_v1_2;
    }

    static boolean arrayContains(String[] array, String value) {
        for (String v: array) {
            if (value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add elements from {@code names} into {@code enabled} if they are in {@code supported}.
     */
    static void addIfSupported(Set<String> supported, List<String> enabled, String... names) {
        for (String n: names) {
            if (supported.contains(n)) {
                enabled.add(n);
            }
        }
    }

    static void useFallbackCiphersIfDefaultIsEmpty(List<String> defaultCiphers, Iterable<String> fallbackCiphers) {
        if (defaultCiphers.isEmpty()) {
            for (String cipher : fallbackCiphers) {
                if (cipher.startsWith("SSL_") || cipher.contains("_RC4_")) {
                    continue;
                }
                defaultCiphers.add(cipher);
            }
        }
    }

    static void useFallbackCiphersIfDefaultIsEmpty(List<String> defaultCiphers, String... fallbackCiphers) {
        useFallbackCiphersIfDefaultIsEmpty(defaultCiphers, asList(fallbackCiphers));
    }

    /**
     * Converts the given exception to a {@link SSLHandshakeException}, if it isn't already.
     */
    static SSLHandshakeException toSSLHandshakeException(Throwable e) {
        if (e instanceof SSLHandshakeException) {
            return (SSLHandshakeException) e;
        }

        return (SSLHandshakeException) new SSLHandshakeException(e.getMessage()).initCause(e);
    }

    /**
     * Return how much bytes can be read out of the encrypted data. Be aware that this method will not increase
     * the readerIndex of the given {@link ByteBuf}.
     *
     * @param   buffer
     *                  The {@link ByteBuf} to read from. Be aware that it must have at least
     *                  {@link #SSL_RECORD_HEADER_LENGTH} bytes to read,
     *                  otherwise it will throw an {@link IllegalArgumentException}.
     * @return length
     *                  The length of the encrypted packet that is included in the buffer or
     *                  {@link #SslUtils#NOT_ENOUGH_DATA} if not enough data is present in the
     *                  {@link ByteBuf}. This will return {@link SslUtils#NOT_ENCRYPTED} if
     *                  the given {@link ByteBuf} is not encrypted at all.
     * @throws IllegalArgumentException
     *                  Is thrown if the given {@link ByteBuf} has not at least {@link #SSL_RECORD_HEADER_LENGTH}
     *                  bytes to read.
     */
    static int getEncryptedPacketLength(ByteBuf buffer, int offset) {
        int packetLength = 0;

        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (buffer.getUnsignedByte(offset)) {
            case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
            case SSL_CONTENT_TYPE_ALERT:
            case SSL_CONTENT_TYPE_HANDSHAKE:
            case SSL_CONTENT_TYPE_APPLICATION_DATA:
            case SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT:
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                tls = false;
        }

        if (tls) {
            // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1 - Check ProtocolVersion
            int majorVersion = buffer.getUnsignedByte(offset + 1);
            if (majorVersion == 3 || buffer.getShort(offset + 1) == GMSSL_PROTOCOL_VERSION) {
                // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1
                packetLength = unsignedShortBE(buffer, offset + 3) + SSL_RECORD_HEADER_LENGTH;
                if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false;
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            int headerLength = (buffer.getUnsignedByte(offset) & 0x80) != 0 ? 2 : 3;
            int majorVersion = buffer.getUnsignedByte(offset + headerLength + 1);
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                packetLength = headerLength == 2 ?
                        (shortBE(buffer, offset) & 0x7FFF) + 2 : (shortBE(buffer, offset) & 0x3FFF) + 3;
                if (packetLength <= headerLength) {
                    return NOT_ENOUGH_DATA;
                }
            } else {
                return NOT_ENCRYPTED;
            }
        }
        return packetLength;
    }

    // Reads a big-endian unsigned short integer from the buffer
    @SuppressWarnings("deprecation")
    private static int unsignedShortBE(ByteBuf buffer, int offset) {
        int value = buffer.getUnsignedShort(offset);
        if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
            value = Integer.reverseBytes(value) >>> Short.SIZE;
        }
        return value;
    }

    // Reads a big-endian short integer from the buffer
    @SuppressWarnings("deprecation")
    private static short shortBE(ByteBuf buffer, int offset) {
        short value = buffer.getShort(offset);
        if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
            value = Short.reverseBytes(value);
        }
        return value;
    }

    private static short unsignedByte(byte b) {
        return (short) (b & 0xFF);
    }

    // Reads a big-endian unsigned short integer from the buffer
    private static int unsignedShortBE(ByteBuffer buffer, int offset) {
        return shortBE(buffer, offset) & 0xFFFF;
    }

    // Reads a big-endian short integer from the buffer
    private static short shortBE(ByteBuffer buffer, int offset) {
        return buffer.order() == ByteOrder.BIG_ENDIAN ?
                buffer.getShort(offset) : ByteBufUtil.swapShort(buffer.getShort(offset));
    }

    static int getEncryptedPacketLength(ByteBuffer[] buffers, int offset) {
        ByteBuffer buffer = buffers[offset];

        // Check if everything we need is in one ByteBuffer. If so we can make use of the fast-path.
        if (buffer.remaining() >= SSL_RECORD_HEADER_LENGTH) {
            return getEncryptedPacketLength(buffer);
        }

        // We need to copy 5 bytes into a temporary buffer so we can parse out the packet length easily.
        ByteBuffer tmp = ByteBuffer.allocate(5);

        do {
            buffer = buffers[offset++].duplicate();
            if (buffer.remaining() > tmp.remaining()) {
                buffer.limit(buffer.position() + tmp.remaining());
            }
            tmp.put(buffer);
        } while (tmp.hasRemaining());

        // Done, flip the buffer so we can read from it.
        tmp.flip();
        return getEncryptedPacketLength(tmp);
    }

    private static int getEncryptedPacketLength(ByteBuffer buffer) {
        int packetLength = 0;
        int pos = buffer.position();
        // SSLv3 or TLS - Check ContentType
        boolean tls;
        switch (unsignedByte(buffer.get(pos))) {
            case SSL_CONTENT_TYPE_CHANGE_CIPHER_SPEC:
            case SSL_CONTENT_TYPE_ALERT:
            case SSL_CONTENT_TYPE_HANDSHAKE:
            case SSL_CONTENT_TYPE_APPLICATION_DATA:
            case SSL_CONTENT_TYPE_EXTENSION_HEARTBEAT:
                tls = true;
                break;
            default:
                // SSLv2 or bad data
                tls = false;
        }

        if (tls) {
            // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1 - Check ProtocolVersion
            int majorVersion = unsignedByte(buffer.get(pos + 1));
            if (majorVersion == 3 || buffer.getShort(pos + 1) == GMSSL_PROTOCOL_VERSION) {
                // SSLv3 or TLS or GMSSLv1.0 or GMSSLv1.1
                packetLength = unsignedShortBE(buffer, pos + 3) + SSL_RECORD_HEADER_LENGTH;
                if (packetLength <= SSL_RECORD_HEADER_LENGTH) {
                    // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                    tls = false;
                }
            } else {
                // Neither SSLv3 or TLSv1 (i.e. SSLv2 or bad data)
                tls = false;
            }
        }

        if (!tls) {
            // SSLv2 or bad data - Check the version
            int headerLength = (unsignedByte(buffer.get(pos)) & 0x80) != 0 ? 2 : 3;
            int majorVersion = unsignedByte(buffer.get(pos + headerLength + 1));
            if (majorVersion == 2 || majorVersion == 3) {
                // SSLv2
                packetLength = headerLength == 2 ?
                        (shortBE(buffer, pos) & 0x7FFF) + 2 : (shortBE(buffer, pos) & 0x3FFF) + 3;
                if (packetLength <= headerLength) {
                    return NOT_ENOUGH_DATA;
                }
            } else {
                return NOT_ENCRYPTED;
            }
        }
        return packetLength;
    }

    static void handleHandshakeFailure(ChannelHandlerContext ctx, Throwable cause, boolean notify) {
        // We have may haven written some parts of data before an exception was thrown so ensure we always flush.
        // See https://github.com/netty/netty/issues/3900#issuecomment-172481830
        ctx.flush();
        if (notify) {
            ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
        }
        ctx.close();
    }

    /**
     * Fills the {@link ByteBuf} with zero bytes.
     */
    static void zeroout(ByteBuf buffer) {
        if (!buffer.isReadOnly()) {
            buffer.setZero(0, buffer.capacity());
        }
    }

    /**
     * Fills the {@link ByteBuf} with zero bytes and releases it.
     */
    static void zerooutAndRelease(ByteBuf buffer) {
        zeroout(buffer);
        buffer.release();
    }

    /**
     * Same as {@link Base64#encode(ByteBuf, boolean)} but allows the use of a custom {@link ByteBufAllocator}.
     *
     * @see Base64#encode(ByteBuf, boolean)
     */
    static ByteBuf toBase64(ByteBufAllocator allocator, ByteBuf src) {
        ByteBuf dst = Base64.encode(src, src.readerIndex(),
                src.readableBytes(), true, Base64Dialect.STANDARD, allocator);
        src.readerIndex(src.writerIndex());
        return dst;
    }

    /**
     * Validate that the given hostname can be used in SNI extension.
     */
    static boolean isValidHostNameForSNI(String hostname) {
        // See  https://datatracker.ietf.org/doc/html/rfc6066#section-3
        return hostname != null &&
               // SNI HostName has to be a FQDN according to TLS SNI Extension spec (see [1]),
               // which means that is has to have at least a host name and a domain part.
               hostname.indexOf('.') > 0 &&
               !hostname.endsWith(".") && !hostname.startsWith("/") &&
               !NetUtil.isValidIpV4Address(hostname) &&
               !NetUtil.isValidIpV6Address(hostname);
    }

    /**
     * Returns {@code true} if the given cipher (in openssl format) is for TLSv1.3, {@code false} otherwise.
     */
    static boolean isTLSv13Cipher(String cipher) {
        // See https://tools.ietf.org/html/rfc8446#appendix-B.4
        return TLSV13_CIPHERS.contains(cipher);
    }

    private SslUtils() {
    }
}
