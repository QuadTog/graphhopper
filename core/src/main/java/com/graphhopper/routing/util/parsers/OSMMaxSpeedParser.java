/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.countryrules.CountryRule;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;
import com.graphhopper.routing.util.spatialrules.CustomArea;
import com.graphhopper.routing.util.spatialrules.SpatialRuleSet;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.Collections;
import java.util.List;

import static com.graphhopper.routing.ev.MaxSpeed.UNSET_SPEED;

public class OSMMaxSpeedParser implements TagParser {

    protected final DecimalEncodedValue carMaxSpeedEnc;

    public OSMMaxSpeedParser() {
        this(MaxSpeed.create());
    }

    public OSMMaxSpeedParser(DecimalEncodedValue carMaxSpeedEnc) {
        if (!carMaxSpeedEnc.isStoreTwoDirections())
            throw new IllegalArgumentException("EncodedValue for maxSpeed must be able to store two directions");

        this.carMaxSpeedEnc = carMaxSpeedEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(carMaxSpeedEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        return handleWayTags(edgeFlags, way, ferry, relationFlags, Collections.emptyList());
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags, List<CustomArea> customAreas) {
        double maxSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed"));

        RoadClass roadClass = RoadClass.find(way.getTag("highway", ""));
        for (CustomArea customArea : customAreas) {
            Object countryCode = customArea.getProperties().get("ISO3166-1:alpha3");
            if (countryCode != null) {
                NewCountry country = NewCountry.valueOf(countryCode.toString());
                CountryRule countryRule = CountryRule.getCountryRule(country);
                if (countryRule != null) {
                    maxSpeed = countryRule.getMaxSpeed(roadClass, TransportationMode.CAR, maxSpeed);
                }
            }
        }
        SpatialRuleSet spatialRuleSet = way.getTag("spatial_rule_set", null);
        if (spatialRuleSet != null && spatialRuleSet != SpatialRuleSet.EMPTY) {
            maxSpeed = spatialRuleSet.getMaxSpeed(roadClass, TransportationMode.CAR, maxSpeed);
        }

        double fwdSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:forward"));
        if (!isValidSpeed(fwdSpeed) && isValidSpeed(maxSpeed))
            fwdSpeed = maxSpeed;
        double maxPossibleSpeed = MaxSpeed.UNLIMITED_SIGN_SPEED;
        if (isValidSpeed(fwdSpeed) && fwdSpeed > maxPossibleSpeed)
            fwdSpeed = maxPossibleSpeed;

        double bwdSpeed = OSMValueExtractor.stringToKmh(way.getTag("maxspeed:backward"));
        if (!isValidSpeed(bwdSpeed) && isValidSpeed(maxSpeed))
            bwdSpeed = maxSpeed;
        if (isValidSpeed(bwdSpeed) && bwdSpeed > maxPossibleSpeed)
            bwdSpeed = maxPossibleSpeed;

        if (!isValidSpeed(fwdSpeed))
            fwdSpeed = UNSET_SPEED;
        carMaxSpeedEnc.setDecimal(false, edgeFlags, fwdSpeed);

        if (!isValidSpeed(bwdSpeed))
            bwdSpeed = UNSET_SPEED;
        carMaxSpeedEnc.setDecimal(true, edgeFlags, bwdSpeed);
        return edgeFlags;
    }
    
    /**
     * @return <i>true</i> if the given speed is not {@link Double#NaN}
     */
    private boolean isValidSpeed(double speed) {
        return !Double.isNaN(speed);
    }
}
