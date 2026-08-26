/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.smartom.agentic.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.erdtman.jcs.JsonCanonicalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * RFC 8785 canonical JSON helper.
 * @author Codex
 * @since 2026-08-25
 */
final class CanonicalJson {
    private CanonicalJson() {
    }

    static CanonicalRecord encode(ObjectMapper mapper, JsonNode value) {
        try {
            byte[] bytes = new JsonCanonicalizer(mapper.writeValueAsBytes(value)).getEncodedUTF8();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new CanonicalRecord(bytes, HexFormat.of().formatHex(digest));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("canonical JSON invalid", exception);
        }
    }

    static int utf8Bytes(JsonNode value) {
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
