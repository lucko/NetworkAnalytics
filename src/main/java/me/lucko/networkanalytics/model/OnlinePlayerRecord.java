/*
 * This file is part of NetworkAnalytics, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.networkanalytics.model;

import lombok.Getter;

import protocolsupport.api.ProtocolVersion;

import java.util.Optional;
import java.util.UUID;

public class OnlinePlayerRecord {

    @Getter
    private UUID uuid;

    @Getter
    private String username;

    private String version;

    public OnlinePlayerRecord(UUID uuid, String username, String version) {
        this.uuid = uuid;
        this.username = username;
        this.version = version;
    }

    public OnlinePlayerRecord(UUID uuid, String username, ProtocolVersion version) {
        this(uuid, username, version.name());
    }

    public OnlinePlayerRecord() {

    }

    public Optional<ProtocolVersion> getVersion() {
        if (this.version == null || this.version.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(ProtocolVersion.valueOf(this.version));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
