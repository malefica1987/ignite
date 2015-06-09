/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.examples.datagrid;

import org.apache.ignite.*;
import org.apache.ignite.examples.*;
import org.apache.ignite.lang.*;

import java.util.*;

/**
 * This example demonstrates some of the cache rich API capabilities.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ignite.{sh|bat} examples/config/example-ignite.xml'}.
 * <p>
 * Alternatively you can run {@link ExampleNodeStartup} in another JVM which will
 * start node with {@code examples/config/example-ignite.xml} configuration.
 */
public class CacheAsyncApiExample {
    /** Cache name. */
    private static final String CACHE_NAME = CacheAsyncApiExample.class.getSimpleName();

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws IgniteException If example execution failed.
     */
    public static void main(String[] args) throws IgniteException {
        try (Ignite ignite = Ignition.start("examples/config/example-ignite.xml")) {
            System.out.println();
            System.out.println(">>> Cache asynchronous API example started.");

            try (IgniteCache<Integer, String> cache = ignite.getOrCreateCache(CACHE_NAME)) {
                // Enable asynchronous mode.
                IgniteCache<Integer, String> asyncCache = cache.withAsync();

                Collection<IgniteFuture<?>> futs = new ArrayList<>();

                // Execute several puts asynchronously.
                for (int i = 0; i < 10; i++) {
                    asyncCache.put(i, String.valueOf(i));

                    futs.add(asyncCache.future());
                }

                // Wait for completion of all futures.
                for (IgniteFuture<?> fut : futs)
                    fut.get();

                // Execute get operation asynchronously.
                asyncCache.get(1);

                // Asynchronously wait for result.
                asyncCache.<String>future().listen(new IgniteInClosure<IgniteFuture<String>>() {
                    @Override public void apply(IgniteFuture<String> fut) {
                        System.out.println("Get operation completed [value=" + fut.get() + ']');
                    }
                });
            }
        }
    }
}
