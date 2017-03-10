/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.driver.internal.cluster;

import java.util.HashSet;
import java.util.Set;

import org.neo4j.driver.internal.net.BoltServerAddress;
import org.neo4j.driver.internal.spi.Connection;
import org.neo4j.driver.internal.spi.ConnectionPool;
import org.neo4j.driver.internal.util.Clock;
import org.neo4j.driver.v1.Logger;
import org.neo4j.driver.v1.exceptions.SecurityException;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;

import static java.lang.String.format;

public class Rediscovery
{
    private static final String NO_ROUTERS_AVAILABLE = "Could not perform discovery. No routing servers available.";

    private final BoltServerAddress initialRouter;
    private final RoutingSettings settings;
    private final Clock clock;
    private final Logger logger;
    private final ClusterCompositionProvider provider;
    private final DnsResolver dnsResolver;

    public Rediscovery( BoltServerAddress initialRouter, RoutingSettings settings, Clock clock, Logger logger,
            ClusterCompositionProvider provider, DnsResolver dnsResolver )
    {
        this.initialRouter = initialRouter;
        this.settings = settings;
        this.clock = clock;
        this.logger = logger;
        this.provider = provider;
        this.dnsResolver = dnsResolver;
    }

    // Given the current routing table and connection pool, use the connection composition provider to fetch a new
    // cluster composition, which would be used to update the routing table and connection pool
    public ClusterComposition lookupClusterComposition( ConnectionPool connections, RoutingTable routingTable )
    {
        int failures = 0;

        for ( long start = clock.millis(), delay = 0; ; delay = Math.max( settings.retryTimeoutDelay, delay * 2 ) )
        {
            long waitTime = start + delay - clock.millis();
            sleep( waitTime );
            start = clock.millis();

            ClusterComposition composition = lookupClusterCompositionOnKnownRouters( connections, routingTable );
            if ( composition != null )
            {
                return composition;
            }

            if ( ++failures >= settings.maxRoutingFailures )
            {
                throw new ServiceUnavailableException( NO_ROUTERS_AVAILABLE );
            }
        }
    }

    private ClusterComposition lookupClusterCompositionOnKnownRouters( ConnectionPool connections,
            RoutingTable routingTable )
    {
        int size = routingTable.routerSize();
        Set<BoltServerAddress> triedServers = new HashSet<>( size );
        for ( int i = 0; i < size; i++ )
        {
            BoltServerAddress address = routingTable.nextRouter();
            if ( address == null )
            {
                break;
            }

            ClusterComposition composition = lookupClusterCompositionOnRouter( address, connections, routingTable );
            if ( composition != null )
            {
                return composition;
            }
            else
            {
                triedServers.add( address );
            }
        }

        Set<BoltServerAddress> ips = dnsResolver.resolve( initialRouter );
        ips.removeAll( triedServers );
        for ( BoltServerAddress address : ips )
        {
            ClusterComposition composition = lookupClusterCompositionOnRouter( address, connections, routingTable );
            if ( composition != null )
            {
                return composition;
            }
        }

        return null;
    }

    private ClusterComposition lookupClusterCompositionOnRouter( BoltServerAddress routerAddress,
            ConnectionPool connections, RoutingTable routingTable )
    {
        ClusterCompositionResponse response;
        try ( Connection connection = connections.acquire( routerAddress ) )
        {
            response = provider.getClusterComposition( connection );
        }
        catch ( SecurityException e )
        {
            // auth error happened, terminate the discovery procedure immediately
            throw e;
        }
        catch ( Throwable t )
        {
            // connection turned out to be broken
            logger.error( format( "Failed to connect to routing server '%s'.", routerAddress ), t );
            routingTable.forget( routerAddress );
            return null;
        }

        ClusterComposition cluster = response.clusterComposition();
        logger.info( "Got cluster composition %s", cluster );
        if ( cluster.hasWriters() )
        {
            return cluster;
        }
        return null;
    }

    private void sleep( long millis )
    {
        if ( millis > 0 )
        {
            try
            {
                clock.sleep( millis );
            }
            catch ( InterruptedException e )
            {
                // restore the interrupted status
                Thread.currentThread().interrupt();
                throw new ServiceUnavailableException( "Thread was interrupted while performing discovery", e );
            }
        }
    }
}
