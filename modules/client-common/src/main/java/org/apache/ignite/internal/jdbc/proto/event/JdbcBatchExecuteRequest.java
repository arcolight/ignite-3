/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.jdbc.proto.event;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.internal.client.proto.ClientMessagePacker;
import org.apache.ignite.internal.client.proto.ClientMessageUnpacker;
import org.apache.ignite.internal.jdbc.proto.ClientMessage;
import org.apache.ignite.internal.tostring.S;
import org.apache.ignite.internal.util.CollectionUtils;

/**
 * JDBC batch execute request.
 */
public class JdbcBatchExecuteRequest extends JdbcObservableTimeAwareRequest implements ClientMessage {
    /** Schema name. */
    private String schemaName;

    /** Sql queries. */
    private List<String> queries;

    /** Flag indicating whether auto-commit mode is enabled. */
    private boolean autoCommit;

    /** Query timeout in milliseconds. */
    private long queryTimeoutMillis;

    /**
     * Token is used to uniquely identify execution request within single connection, which is required to properly coordinate cancellation
     * request.
     */
    private long correlationToken;

    /**
     * Default constructor.
     */
    public JdbcBatchExecuteRequest() {
    }

    /**
     * Constructor.
     *
     * @param schemaName Schema name.
     * @param queries Queries.
     * @param autoCommit Flag indicating whether auto-commit mode is enabled.
     * @param queryTimeoutMillis Query timeout in millseconds.
     * @param correlationToken Token is used to uniquely identify execution request within single connection.
     */
    public JdbcBatchExecuteRequest(
            String schemaName,
            List<String> queries,
            boolean autoCommit,
            long queryTimeoutMillis,
            long correlationToken
    ) {
        assert !CollectionUtils.nullOrEmpty(queries);

        this.schemaName = schemaName;
        this.queries = queries;
        this.autoCommit = autoCommit;
        this.queryTimeoutMillis = queryTimeoutMillis;
        this.correlationToken = correlationToken;
    }

    /**
     * Get the schema name.
     *
     * @return Schema name.
     */
    public String schemaName() {
        return schemaName;
    }

    /**
     * Get the queries.
     *
     * @return Queries.
     */
    public List<String> queries() {
        return queries;
    }

    /**
     * Get flag indicating whether auto-commit mode is enabled.
     *
     * @return {@code true} if auto-commit mode is enabled, {@code false} otherwise.
     */
    public boolean autoCommit() {
        return autoCommit;
    }

    /**
     * Returns the timeout in milliseconds.
     *
     * @return Timeout in milliseconds.
     */
    public long queryTimeoutMillis() {
        return queryTimeoutMillis;
    }

    public long correlationToken() {
        return correlationToken;
    }

    /** {@inheritDoc} */
    @Override
    public void writeBinary(ClientMessagePacker packer) {
        packer.packBoolean(autoCommit);
        ClientMessageUtils.writeStringNullable(packer, schemaName);

        packer.packInt(queries.size());

        for (String q : queries) {
            packer.packString(q);
        }

        packer.packLong(queryTimeoutMillis);
        packer.packLong(correlationToken);
    }

    /** {@inheritDoc} */
    @Override
    public void readBinary(ClientMessageUnpacker unpacker) {
        autoCommit = unpacker.unpackBoolean();
        schemaName = ClientMessageUtils.readStringNullable(unpacker);

        int n = unpacker.unpackInt();

        queries = new ArrayList<>(n);

        for (int i = 0; i < n; ++i) {
            queries.add(unpacker.unpackString());
        }

        queryTimeoutMillis = unpacker.unpackLong();
        correlationToken = unpacker.unpackLong();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return S.toString(JdbcBatchExecuteRequest.class, this);
    }
}
