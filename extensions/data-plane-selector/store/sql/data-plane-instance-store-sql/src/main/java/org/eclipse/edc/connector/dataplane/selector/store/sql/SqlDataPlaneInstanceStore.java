/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *
 */

package org.eclipse.edc.connector.dataplane.selector.store.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.connector.dataplane.selector.store.sql.schema.DataPlaneInstanceStatements;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.store.AbstractSqlStore;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * SQL store implementation of {@link DataPlaneInstanceStore}
 */
public class SqlDataPlaneInstanceStore extends AbstractSqlStore implements DataPlaneInstanceStore {

    private final DataPlaneInstanceStatements statements;

    public SqlDataPlaneInstanceStore(DataSourceRegistry dataSourceRegistry, String dataSourceName,
                                     TransactionContext transactionContext, DataPlaneInstanceStatements statements,
                                     ObjectMapper objectMapper, QueryExecutor queryExecutor) {
        super(dataSourceRegistry, dataSourceName, transactionContext, objectMapper, queryExecutor);
        this.statements = Objects.requireNonNull(statements);
    }

    @Override
    public StoreResult<Void> create(DataPlaneInstance instance) {

        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {

                if (findByIdInternal(connection, instance.getId()) == null) {
                    insert(connection, instance);
                    return StoreResult.success();
                } else {
                    return StoreResult.alreadyExists(format(DATA_PLANE_INSTANCE_EXISTS, instance.getId()));
                }

            } catch (Exception exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public StoreResult<Void> update(DataPlaneInstance instance) {

        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {

                if (findByIdInternal(connection, instance.getId()) == null) {
                    return StoreResult.notFound(format(DATA_PLANE_INSTANCE_NOT_FOUND, instance.getId()));
                } else {
                    update(connection, instance);
                    return StoreResult.success();
                }

            } catch (Exception exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public DataPlaneInstance findById(String id) {
        Objects.requireNonNull(id);
        return transactionContext.execute(() -> {
            try (var connection = getConnection()) {
                return findByIdInternal(connection, id);

            } catch (Exception exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    @Override
    public Stream<DataPlaneInstance> getAll() {
        return transactionContext.execute(() -> {
            try {
                var sql = statements.getAllTemplate();
                return queryExecutor.query(getConnection(), true, this::mapResultSet, sql);
            } catch (SQLException exception) {
                throw new EdcPersistenceException(exception);
            }
        });
    }

    private DataPlaneInstance findByIdInternal(Connection connection, String id) {
        var sql = statements.getFindByIdTemplate();
        return queryExecutor.single(connection, false, this::mapResultSet, sql, id);
    }

    private void insert(Connection connection, DataPlaneInstance instance) {
        var sql = statements.getInsertTemplate();
        queryExecutor.execute(connection, sql, instance.getId(), toJson(instance));
    }

    private void update(Connection connection, DataPlaneInstance instance) {
        var sql = statements.getUpdateTemplate();
        queryExecutor.execute(connection, sql, toJson(instance), instance.getId());
    }


    private DataPlaneInstance mapResultSet(ResultSet resultSet) throws Exception {
        var json = resultSet.getString(statements.getDataColumn());
        return fromJson(json, DataPlaneInstance.class);
    }


}
