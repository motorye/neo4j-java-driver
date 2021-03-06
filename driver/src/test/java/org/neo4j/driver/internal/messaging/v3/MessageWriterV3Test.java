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
package org.neo4j.driver.internal.messaging.v3;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.stream.Stream;

import org.neo4j.driver.Query;
import org.neo4j.driver.internal.InternalBookmark;
import org.neo4j.driver.internal.messaging.Message;
import org.neo4j.driver.internal.messaging.MessageFormat;
import org.neo4j.driver.internal.messaging.request.BeginMessage;
import org.neo4j.driver.internal.messaging.request.HelloMessage;
import org.neo4j.driver.internal.messaging.request.InitMessage;
import org.neo4j.driver.internal.messaging.request.RunMessage;
import org.neo4j.driver.internal.packstream.PackOutput;
import org.neo4j.driver.internal.security.InternalAuthToken;
import org.neo4j.driver.internal.util.messaging.AbstractMessageWriterTestBase;

import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.neo4j.driver.AccessMode.READ;
import static org.neo4j.driver.AccessMode.WRITE;
import static org.neo4j.driver.AuthTokens.basic;
import static org.neo4j.driver.Values.point;
import static org.neo4j.driver.Values.value;
import static org.neo4j.driver.internal.DatabaseNameUtil.defaultDatabase;
import static org.neo4j.driver.internal.messaging.request.CommitMessage.COMMIT;
import static org.neo4j.driver.internal.messaging.request.DiscardAllMessage.DISCARD_ALL;
import static org.neo4j.driver.internal.messaging.request.GoodbyeMessage.GOODBYE;
import static org.neo4j.driver.internal.messaging.request.PullAllMessage.PULL_ALL;
import static org.neo4j.driver.internal.messaging.request.ResetMessage.RESET;
import static org.neo4j.driver.internal.messaging.request.RollbackMessage.ROLLBACK;
import static org.neo4j.driver.internal.messaging.request.RunWithMetadataMessage.autoCommitTxRunMessage;
import static org.neo4j.driver.internal.messaging.request.RunWithMetadataMessage.unmanagedTxRunMessage;

class MessageWriterV3Test extends AbstractMessageWriterTestBase
{
    @Override
    protected MessageFormat.Writer newWriter( PackOutput output )
    {
        return new MessageWriterV3( output );
    }

    @Override
    protected Stream<Message> supportedMessages()
    {
        return Stream.of(
                // Bolt V3 messages
                new HelloMessage( "MyDriver/1.2.3", ((InternalAuthToken) basic( "neo4j", "neo4j" )).toMap(), Collections.emptyMap() ),
                GOODBYE,
                new BeginMessage( InternalBookmark.parse( "neo4j:bookmark:v1:tx123" ), ofSeconds( 5 ), singletonMap( "key", value( 42 ) ), READ, defaultDatabase() ),
                new BeginMessage( InternalBookmark.parse( "neo4j:bookmark:v1:tx123" ), ofSeconds( 5 ), singletonMap( "key", value( 42 ) ), WRITE, defaultDatabase() ),
                COMMIT,
                ROLLBACK,
                autoCommitTxRunMessage( new Query( "RETURN 1" ), ofSeconds( 5 ), singletonMap( "key", value( 42 ) ), defaultDatabase(), READ,
                        InternalBookmark.parse( "neo4j:bookmark:v1:tx1" ) ),
                autoCommitTxRunMessage( new Query( "RETURN 1" ), ofSeconds( 5 ), singletonMap( "key", value( 42 ) ), defaultDatabase(), WRITE,
                        InternalBookmark.parse( "neo4j:bookmark:v1:tx1" ) ),
                unmanagedTxRunMessage( new Query( "RETURN 1" ) ),
                PULL_ALL,
                DISCARD_ALL,
                RESET,

                // Bolt V3 messages with struct values
                autoCommitTxRunMessage( new Query( "RETURN $x", singletonMap( "x", value( ZonedDateTime.now() ) ) ), ofSeconds( 1 ), emptyMap(),
                        defaultDatabase(), READ, InternalBookmark.empty() ),
                autoCommitTxRunMessage( new Query( "RETURN $x", singletonMap( "x", value( ZonedDateTime.now() ) ) ), ofSeconds( 1 ), emptyMap(),
                        defaultDatabase(), WRITE, InternalBookmark.empty() ),
                unmanagedTxRunMessage( new Query( "RETURN $x", singletonMap( "x", point( 42, 1, 2, 3 ) )  ) )
        );
    }

    @Override
    protected Stream<Message> unsupportedMessages()
    {
        return Stream.of(
                // Bolt V1 and V2 messages
                new InitMessage( "Apa", emptyMap() ),
                new RunMessage( "RETURN 1" )
        );
    }
}
