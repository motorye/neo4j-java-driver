/*
 * Copyright (c) 2002-2018 "Neo Technology,"
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
package org.neo4j.driver.internal;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.neo4j.driver.internal.spi.Connection;
import org.neo4j.driver.internal.util.Futures;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.StatementResultCursor;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.summary.ResultSummary;
import org.neo4j.driver.v1.util.Function;

public class InternalStatementResult implements StatementResult
{
    private final Connection connection;
    private final StatementResultCursor cursor;
    private List<String> keys;

    public InternalStatementResult( Connection connection, StatementResultCursor cursor )
    {
        this.connection = connection;
        this.cursor = cursor;
    }

    @Override
    public List<String> keys()
    {
        if ( keys == null )
        {
            blockingGet( cursor.peekAsync() );
            keys = cursor.keys();
        }
        return keys;
    }

    @Override
    public boolean hasNext()
    {
        return blockingGet( cursor.peekAsync() ) != null;
    }

    @Override
    public Record next()
    {
        Record record = blockingGet( cursor.nextAsync() );
        if ( record == null )
        {
            throw new NoSuchRecordException( "No more records" );
        }
        return record;
    }

    @Override
    public Record single()
    {
        return blockingGet( cursor.singleAsync() );
    }

    @Override
    public Record peek()
    {
        Record record = blockingGet( cursor.peekAsync() );
        if ( record == null )
        {
            throw new NoSuchRecordException( "Cannot peek past the last record" );
        }
        return record;
    }

    @Override
    public List<Record> list()
    {
        return blockingGet( cursor.listAsync() );
    }

    @Override
    public <T> List<T> list( Function<Record, T> mapFunction )
    {
        return blockingGet( cursor.listAsync( mapFunction ) );
    }

    @Override
    public ResultSummary consume()
    {
        return blockingGet( cursor.consumeAsync() );
    }

    @Override
    public ResultSummary summary()
    {
        return blockingGet( cursor.summaryAsync() );
    }

    @Override
    public void remove()
    {
        throw new ClientException( "Removing records from a result is not supported." );
    }

    private <T> T blockingGet( CompletionStage<T> stage )
    {
        return Futures.blockingGet( stage, this::terminateConnectionOnThreadInterrupt );
    }

    private void terminateConnectionOnThreadInterrupt()
    {
        connection.terminateAndRelease( "Thread interrupted while waiting for result to arrive" );
    }
}
