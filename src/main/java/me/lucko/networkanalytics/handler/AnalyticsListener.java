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

package me.lucko.networkanalytics.handler;

import lombok.RequiredArgsConstructor;

import me.lucko.helper.Events;
import me.lucko.helper.metadata.Metadata;
import me.lucko.helper.terminable.TerminableConsumer;
import me.lucko.helper.terminable.module.TerminableModule;
import me.lucko.networkanalytics.AnalyticsPlugin;
import me.lucko.networkanalytics.NetworkAnalytics;

import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import protocolsupport.api.ProtocolSupportAPI;
import protocolsupport.api.ProtocolVersion;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

@RequiredArgsConstructor
public class AnalyticsListener implements TerminableModule {
    private final AnalyticsPlugin plugin;

    @Override
    public void setup(@Nonnull TerminableConsumer consumer) {
        Events.subscribe(PlayerLoginEvent.class, EventPriority.MONITOR)
                .filter(e -> e.getResult() == PlayerLoginEvent.Result.ALLOWED)
                .handler(e -> {
                    plugin.getDataManager().logPlayer(e.getPlayer().getUniqueId(), e.getPlayer().getName());
                    Metadata.provideForPlayer(e.getPlayer()).put(NetworkAnalytics.CONNECTION_TIME_SECONDS, (System.currentTimeMillis() / 1000L));
                })
                .bindWith(consumer);

        Events.subscribe(PlayerJoinEvent.class, EventPriority.MONITOR)
                .handler(e -> {
                    ProtocolVersion protocolVersion = ProtocolSupportAPI.getProtocolVersion(e.getPlayer());
                    if (protocolVersion != null) {
                        Metadata.provideForPlayer(e.getPlayer()).put(NetworkAnalytics.PROTOCOL_VERSION, protocolVersion);
                    }
                })
                .bindWith(consumer);

        Events.subscribe(PlayerQuitEvent.class)
                .handler(e -> {
                    Long loginTime = Metadata.provideForPlayer(e.getPlayer()).getOrNull(NetworkAnalytics.CONNECTION_TIME_SECONDS);
                    long now = System.currentTimeMillis() / 1000L;
                    if (loginTime != null) {
                        long diff = now - loginTime;
                        int mins = (int) TimeUnit.SECONDS.toMinutes(diff);
                        if (mins > 0) {
                            plugin.getDataManager().incrementPlayerMinutesPlayed(e.getPlayer().getUniqueId(), mins);
                        }
                    }
                })
                .bindWith(consumer);
    }
}
