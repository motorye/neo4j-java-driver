/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.driver.internal.metrics;

import org.neo4j.driver.Logging;
import org.neo4j.driver.internal.util.Clock;
import org.neo4j.driver.Metrics;

public class InternalMetricsProvider implements MetricsProvider
{
    private final InternalMetrics metrics;

    public InternalMetricsProvider( Clock clock, Logging logging )
    {
        this.metrics = new InternalMetrics( clock, logging );
    }

    @Override
    public Metrics metrics()
    {
        return metrics;
    }

    @Override
    public MetricsListener metricsListener()
    {
        return metrics;
    }

    @Override
    public boolean isMetricsEnabled()
    {
        return true;
    }
}