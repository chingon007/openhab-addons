/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.haassohnpelletstove.internal;

import static org.openhab.binding.haassohnpelletstove.internal.HaasSohnpelletstoveBindingConstants.*;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HaasSohnpelletstoveHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Christian Feininger - Initial contribution
 */
@NonNullByDefault
public class HaasSohnpelletstoveHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(HaasSohnpelletstoveHandler.class);

    private @Nullable ScheduledFuture<?> refreshJob;

    private HaasSohnpelletstoveConfiguration config = new HaasSohnpelletstoveConfiguration();
    boolean resultOk = false;

    private HaasSohnpelletstoveJSONCommunication serviceCommunication;

    private boolean automaticRefreshing = false;

    private Map<String, Boolean> linkedChannels = new HashMap<String, Boolean>();

    public HaasSohnpelletstoveHandler(Thing thing) {
        super(thing);
        serviceCommunication = new HaasSohnpelletstoveJSONCommunication();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(CHANNELPOWER)) {
            String postData = null;
            if (command.equals(OnOffType.ON)) {
                postData = "{\"prg\":true}";
            } else if (command.equals(OnOffType.OFF)) {
                postData = "{\"prg\":false}";
            }
            if (postData != null) {
                logger.debug("Executing {} command", CHANNELPOWER);
                updateOvenData(postData);
            }
        } else if (channelUID.getId().equals(CHANNELSPTEMP)) {
            if (command instanceof QuantityType<?>) {
                QuantityType<?> value = null;
                value = (QuantityType<?>) command;
                double a = value.doubleValue();

                String postdata = "{\"sp_temp\":" + a + "}";
                logger.debug("Executing {} command", CHANNELSPTEMP);
                updateOvenData(postdata);
            } else {
                logger.debug("Error. Command is the wrong type: {}", command.toString());
            }
        } else if (channelUID.getId().equals(CHANNELECOMODE)) {
            String postData = null;
            if (command.equals(OnOffType.ON)) {
                postData = "{\"eco_mode\":true}";
            } else if (command.equals(OnOffType.OFF)) {
                postData = "{\"eco_mode\":false}";
            }
            if (postData != null) {
                logger.debug("Executing {} command", CHANNELECOMODE);
                updateOvenData(postData);
            }
        }
    }

    /**
     * Calls the service to update the oven data
     *
     * @param postdata
     */
    private boolean updateOvenData(@Nullable String postdata) {
        Helper message = new Helper();
        if (serviceCommunication.updateOvenData(postdata, message, this.getThing().getUID().toString())) {
            updateStatus(ThingStatus.ONLINE);
            return true;
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    message.getStatusDesription());
            return false;
        }
    }

    @Override
    public void initialize() {
        logger.info("Initializing haassohnpelletstove handler for thing {}", getThing().getUID());
        config = getConfigAs(HaasSohnpelletstoveConfiguration.class);
        boolean validConfig = true;
        String errors = "";
        String statusDescr = null;
        if (config.refreshRate < 0 && config.refreshRate > 999) {
            errors += " Parameter 'refresh Rate' greater then 0 and less then 1000.";
            statusDescr = "Parameter 'refresh Rate' greater then 0 and less then 1000.";
            validConfig = false;
        }
        if (config.hostIP == null) {
            errors += " Parameter 'hostIP' must be configured.";
            statusDescr = "IP Address must be configured!";
            validConfig = false;
        } else if (!new IpAddressValidator().isValid(config.hostIP)) {
            errors += " 'hostIP' is no valid IP address.";
            statusDescr = "No valid IP-Adress configured.";
            validConfig = false;
        }
        if (config.hostPIN == null) {
            errors += " Parameter 'hostPin' must be configured.";
            statusDescr = "PIN must be configured!";
            validConfig = false;
        } else if (!new PinValidator().isValid(config.hostPIN)) {
            errors += " 'hostPIN' is no valid PIN. PIN consists of 4-digit numbers.";
            statusDescr = "No valid PIN configure. A valid PIN consists of 4-digit numbers.";
            validConfig = false;
        }
        errors = errors.trim();
        Helper message = new Helper();
        message.setStatusDescription(statusDescr);
        if (validConfig) {
            serviceCommunication.setConfig(config);
            if (serviceCommunication.refreshOvenConnection(message, this.getThing().getUID().toString())) {
                if (updateOvenData(null)) {
                    updateStatus(ThingStatus.ONLINE);
                    updateLinkedChannels();
                }
            } else {
                logger.info("Setting thing '{}' to OFFLINE: {}", getThing().getUID(), errors);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message.getStatusDesription());
            }
        } else {
            logger.info("Setting thing '{}' to OFFLINE: {}", getThing().getUID(), errors);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message.getStatusDesription());
        }
    }

    private void updateLinkedChannels() {
        verifyLinkedChannel(CHANNEL_ISTEMP);
        verifyLinkedChannel(CHANNELMODE);
        verifyLinkedChannel(CHANNELPOWER);
        verifyLinkedChannel(CHANNELSPTEMP);
        verifyLinkedChannel(CHANNELECOMODE);
        verifyLinkedChannel(CHANNELIGNITIONS);
        verifyLinkedChannel(CHANNELMAINTENANCEIN);
        verifyLinkedChannel(CHANNELCLEANINGIN);
        verifyLinkedChannel(CHANNELCONSUMPTION);
        verifyLinkedChannel(CHANNELONTIME);
        if (!linkedChannels.isEmpty()) {
            logger.info("Start automatic refreshing");
            updateOvenData(null);
            for (Channel channel : getThing().getChannels()) {
                updateChannel(channel.getUID().getId());
            }
            startAutomaticRefresh();
            automaticRefreshing = true;
        }
    }

    private void verifyLinkedChannel(String channelID) {
        if (isLinked(channelID) && !linkedChannels.containsKey(channelID)) {
            linkedChannels.put(channelID, true);
        }
    }

    @Override
    public void dispose() {
        logger.info("Disposing Haas and Sohn Pellet stove handler.");
        stopScheduler();
    }

    private void stopScheduler() {
        ScheduledFuture<?> job = refreshJob;
        if (job != null) {
            job.cancel(true);
        }
        refreshJob = null;
    }

    /**
     * Start the job refreshing the oven status
     */
    private void startAutomaticRefresh() {
        ScheduledFuture<?> job = refreshJob;
        if (job == null || job.isCancelled()) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    updateOvenData(null);

                    for (Channel channel : getThing().getChannels()) {
                        updateChannel(channel.getUID().getId());
                    }
                }
            };
            int period = config.refreshRate;
            refreshJob = scheduler.scheduleWithFixedDelay(runnable, 0, period, TimeUnit.SECONDS);
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        if (!automaticRefreshing) {
            logger.debug("Start automatic refreshing");
            startAutomaticRefresh();
            automaticRefreshing = true;
        }
        verifyLinkedChannel(channelUID.getId());
        updateChannel(channelUID.getId());
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        linkedChannels.remove(channelUID.getId());
        if (linkedChannels.isEmpty()) {
            automaticRefreshing = false;
            stopScheduler();
            logger.info("Stop automatic refreshing");
        }
    }

    private void updateChannel(String channelId) {
        if (isLinked(channelId)) {
            State state = null;
            HaasSohnpelletstoveJsonData data = serviceCommunication.getOvenData();
            if (data != null) {
                switch (channelId) {
                    case CHANNEL_ISTEMP:
                        state = new QuantityType<>(Double.valueOf(data.getisTemp()), SIUnits.CELSIUS);
                        update(state, channelId);
                        break;
                    case CHANNELMODE:
                        state = new StringType(data.getMode());
                        update(state, channelId);
                        break;
                    case CHANNELPOWER:
                        update(OnOffType.from(data.getPrg()), channelId);
                        break;
                    case CHANNELECOMODE:
                        update(OnOffType.from(data.getEcoMode()), channelId);
                        break;
                    case CHANNELSPTEMP:
                        state = new QuantityType<>(Double.valueOf(data.getspTemp()), SIUnits.CELSIUS);
                        update(state, channelId);
                        break;
                    case CHANNELCLEANINGIN:
                        String cleaning = data.getCleaningIn();
                        double time = Double.parseDouble(cleaning);
                        time = time / 60;
                        DecimalFormat df = new DecimalFormat("0.00");
                        state = new StringType(df.format(time));
                        update(state, channelId);
                        break;
                    case CHANNELCONSUMPTION:
                        state = new StringType(data.getConsumption());
                        update(state, channelId);
                        break;
                    case CHANNELIGNITIONS:
                        state = new StringType(data.getIgnitions());
                        update(state, channelId);
                        break;
                    case CHANNELMAINTENANCEIN:
                        state = new StringType(data.getMaintenanceIn());
                        update(state, channelId);
                        break;
                    case CHANNELONTIME:
                        state = new StringType(data.getOnTime());
                        update(state, channelId);
                        break;
                }
            }
        }
    }

    /**
     * Updates the State of the given channel
     *
     * @param state
     * @param channelId
     */
    private void update(@Nullable State state, String channelId) {
        logger.debug("Update channel {} with state {}", channelId, (state == null) ? "null" : state.toString());

        if (state != null) {
            updateState(channelId, state);

        } else {
            updateState(channelId, UnDefType.NULL);
        }
    }
}