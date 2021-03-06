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
package org.openhab.binding.boschshc.internal.services.dto;

/**
 * Generic error response of the Bosch REST API.
 * 
 * @author Christian Oeing - Initial contribution
 */
public class JsonRestExceptionResponse extends BoschSHCServiceState {
    public JsonRestExceptionResponse() {
        super("JsonRestExceptionResponseEntity");
        this.errorCode = "";
        this.statusCode = 0;
    }

    /**
     * The error code of the occurred Exception.
     */
    public String errorCode;

    /**
     * The HTTP status of the error.
     */
    public int statusCode;
}
