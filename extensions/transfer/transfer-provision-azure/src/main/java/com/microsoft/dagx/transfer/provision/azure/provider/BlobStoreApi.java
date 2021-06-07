/*
 * Copyright (c) Microsoft Corporation.
 *  All rights reserved.
 *
 */

package com.microsoft.dagx.transfer.provision.azure.provider;

import com.azure.storage.blob.models.BlobItem;

import java.time.OffsetDateTime;
import java.util.List;

public interface BlobStoreApi {

    void createContainer(String accountName, String containerName);

    void deleteContainer(String accountName, String containerName);

    boolean exists(String accountName, String containerName);

    String createContainerSasToken(String accountName, String containerName, String accessSpec, OffsetDateTime expiry);

    List<BlobItem> listContainer(String accountName, String containerName);
}
