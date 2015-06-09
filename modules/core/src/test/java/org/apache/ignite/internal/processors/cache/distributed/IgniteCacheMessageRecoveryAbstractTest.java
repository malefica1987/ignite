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

package org.apache.ignite.internal.processors.cache.distributed;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.util.nio.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.spi.communication.tcp.*;
import org.apache.ignite.testframework.*;
import org.apache.ignite.testframework.junits.common.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.apache.ignite.cache.CacheAtomicWriteOrderMode.*;
import static org.apache.ignite.cache.CacheMode.*;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.*;

/**
 * Tests message delivery after reconnection.
 */
public abstract class IgniteCacheMessageRecoveryAbstractTest extends GridCommonAbstractTest {
    /** Grid count. */
    public static final int GRID_CNT = 3;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        TcpCommunicationSpi commSpi = new TcpCommunicationSpi();

        commSpi.setSocketWriteTimeout(1000);

        cfg.setCommunicationSpi(commSpi);

        CacheConfiguration ccfg = defaultCacheConfiguration();

        ccfg.setCacheMode(PARTITIONED);
        ccfg.setAtomicityMode(atomicityMode());
        ccfg.setBackups(1);
        ccfg.setAtomicWriteOrderMode(PRIMARY);
        ccfg.setNearConfiguration(null);
        ccfg.setWriteSynchronizationMode(FULL_SYNC);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /**
     * @return Cache atomicity mode.
     */
    protected abstract CacheAtomicityMode atomicityMode();

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        startGrids(GRID_CNT);
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        for (int i = 0; i < GRID_CNT; i++) {
            final IgniteKernal grid = (IgniteKernal)grid(i);

            GridTestUtils.retryAssert(log, 10, 100, new CA() {
                @Override public void apply() {
                    assertTrue(grid.internalCache().context().mvcc().atomicFutures().isEmpty());
                }
            });
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testMessageRecovery() throws Exception {
        final Ignite ignite = grid(0);

        final IgniteCache<Object, String> cache = ignite.cache(null);

        Map<Integer, String> map = new HashMap<>();

        for (int i = 0; i < 1000; i++)
            map.put(i, "0");

        cache.putAll(map);

        final AtomicBoolean stop = new AtomicBoolean();

        IgniteInternalFuture<?> fut = GridTestUtils.runAsync(new Callable<Object>() {
            @Override public Object call() throws Exception {
                Thread.currentThread().setName("update-thread");

                ThreadLocalRandom rnd = ThreadLocalRandom.current();

                int iter = 0;

                while (!stop.get()) {
                    Map<Integer, String> map = new HashMap<>();

                    for (int i = 0; i < 100; i++)
                        map.put(rnd.nextInt(0, 1000), String.valueOf(i));

                    cache.putAll(map);

                    if (++iter % 100 == 0)
                        log.info("Iteration: " + iter);
                }

                return null;
            }
        });

        try {
            for (int i = 0; i < 30; i++) {
                Thread.sleep(1000);

                closeSessions();
            }
        }
        finally {
            stop.set(true);
        }

        fut.get();
    }

    /**
     * @throws Exception If failed.
     */
    private void closeSessions() throws Exception {
        Ignite ignite = ignite(ThreadLocalRandom.current().nextInt(0, GRID_CNT));

        log.info("Close sessions for: " + ignite.name());

        TcpCommunicationSpi commSpi = (TcpCommunicationSpi)ignite.configuration().getCommunicationSpi();

        Map<UUID, GridCommunicationClient> clients = U.field(commSpi, "clients");

        assertTrue(clients.size() > 0);

        for (GridCommunicationClient client : clients.values()) {
            GridTcpNioCommunicationClient client0 = (GridTcpNioCommunicationClient)client;

            GridNioSession ses = client0.session();

            ses.close();
        }
    }
}
