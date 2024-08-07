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

package org.apache.ignite.internal.catalog.compaction.message;

import org.apache.ignite.internal.network.annotations.MessageGroup;

/**
 * Message types used in catalog compaction module.
 */
@MessageGroup(groupType = CatalogCompactionMessageGroup.GROUP_TYPE, groupName = "CatalogCompactionMessages")
public class CatalogCompactionMessageGroup {
    public static final short GROUP_TYPE = 14;

    /** See {@link CatalogMinimumRequiredTimeRequest} for the details. */
    public static final short MINIMUM_REQUIRED_TIME_REQUEST = 0;

    /** See {@link CatalogMinimumRequiredTimeResponse} for the details. */
    public static final short MINIMUM_REQUIRED_TIME_RESPONSE = 1;
}
