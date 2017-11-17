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

package me.lucko.networkanalytics;

import lombok.Getter;

import me.lucko.helper.Commands;
import me.lucko.helper.Scheduler;
import me.lucko.helper.messaging.Channel;
import me.lucko.helper.messaging.ChannelAgent;
import me.lucko.helper.messaging.InstanceData;
import me.lucko.helper.metadata.Metadata;
import me.lucko.helper.plugin.ExtendedJavaPlugin;
import me.lucko.helper.plugin.ap.Plugin;
import me.lucko.helper.redis.HelperRedis;
import me.lucko.helper.sql.HelperDataSource;
import me.lucko.helper.utils.Players;
import me.lucko.networkanalytics.channel.AnalyticsData;
import me.lucko.networkanalytics.channel.OnlinePlayerRecord;
import me.lucko.networkanalytics.data.DataManager;
import me.lucko.networkanalytics.handler.AnalyticsCommand;
import me.lucko.networkanalytics.handler.AnalyticsListener;

import protocolsupport.api.ProtocolVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

@Plugin(
        name = "NetworkAnalytics",
        hardDepends = {
                "helper",
                "helper-sql",
                "helper-redis",
                "ProtocolSupport"
        }
)
public class AnalyticsPlugin extends ExtendedJavaPlugin implements NetworkAnalytics {

    private InstanceData instanceData;

    @Getter
    private DataManager dataManager;

    @Getter
    private Map<String, AnalyticsData> analyticsDataMap = new ConcurrentHashMap<>();

    private Channel<AnalyticsData> analyticsChannel;

    @Override
    public void enable() {

        // get instance data
        instanceData = getService(InstanceData.class);
        if (instanceData == null) {
            String name = loadConfig("config.yml").getString("server-id", "null");
            instanceData = new InstanceData() {
                @Nonnull
                @Override
                public String getId() {
                    return name;
                }

                @Nonnull
                @Override
                public Set<String> getGroups() {
                    return Collections.emptySet();
                }
            };
        }

        // get sql source
        HelperDataSource sql = getService(HelperDataSource.class);

        // init data manager
        dataManager = new DataManager(this, sql);
        dataManager.init();

        bindComposite(new AnalyticsListener(this));

        // get messaging channels
        HelperRedis redis = getService(HelperRedis.class);
        analyticsChannel = redis.getChannel("na-data", AnalyticsData.class);

        // send monitoring data periodically
        Scheduler.runTaskRepeatingSync(() -> {
            AnalyticsData data = formData();
            analyticsChannel.sendMessage(data);
        }, 70L, 90L).bindWith(this);

        // listen for analytics data
        ChannelAgent<AnalyticsData> analyticsChannelAgent = analyticsChannel.newAgent();
        analyticsChannelAgent.bindWith(this);
        analyticsChannelAgent.addListener((agent, message) -> analyticsDataMap.put(message.getServerId(), message));

        // cleanup old analytics data
        Scheduler.runTaskRepeatingAsync(() -> {
            long expiry = (System.currentTimeMillis() / 1000L) - 20;
            analyticsDataMap.values().removeIf(data -> data.getTimeSent() < expiry);
        }, 35L, 40L);

        registerCommand(new AnalyticsCommand(this), "analytics");

        Commands.create()
                .assertPermission("networkanalytics.playerversion")
                .assertUsage("<player>")
                .handler(c -> {
                    String player = c.rawArg(0);
                    Scheduler.runAsync(() -> {
                        OnlinePlayerRecord record = null;

                        search:
                        for (AnalyticsData data : analyticsDataMap.values()) {
                            for (OnlinePlayerRecord r : data.getPlayers()) {
                                if (r.getUsername().equalsIgnoreCase(player) || r.getUuid().toString().equalsIgnoreCase(player)) {
                                    record = r;
                                    break search;
                                }
                            }
                        }

                        if (record == null) {
                            Players.msg(c.sender(), "&3[ANALYTICS] &fNo player found with the username/uuid '" + player + "'");
                            return;
                        }

                        Players.msg(c.sender(), "&3[ANALYTICS] &fPlayer &b" + record.getUsername() + " &fis playing on version &b" + getProtocolName(record.getVersion().orElse(null)) + "&f.");
                    });
                })
                .register(this, "playerversion");

        provideService(NetworkAnalytics.class, this);
    }

    private AnalyticsData formData() {
        String serverId = instanceData.getId();
        long time = System.currentTimeMillis() / 1000L;

        List<OnlinePlayerRecord> records = new ArrayList<>();
        Players.forEach(p -> {
            ProtocolVersion version = Metadata.provideForPlayer(p).getOrNull(NetworkAnalytics.PROTOCOL_VERSION);
            String locale = p.getLocale();
            if (locale == null || locale.equals("null")) {
                locale = "undisclosed";
            }
            records.add(new OnlinePlayerRecord(p.getUniqueId(), p.getName(), version, locale.toLowerCase()));
        });

        return new AnalyticsData(serverId, time, records);
    }

    public String getInstanceId() {
        return instanceData.getId();
    }

    public static String getProtocolName(ProtocolVersion version) {
        if (version != null) {
            String name = version.getName();
            if (name != null) {
                return name;
            } else {
                return version.name();
            }
        }
        return "Unknown";
    }

}
