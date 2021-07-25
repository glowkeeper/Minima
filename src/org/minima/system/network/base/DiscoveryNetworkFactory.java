/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.minima.system.network.base;

import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.libp2p.core.crypto.PrivKey;

// import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
// import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
// import tech.pegasys.teku.infrastructure.async.SafeFuture;
// import tech.pegasys.teku.infrastructure.async.Waiter;
// import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
// import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
// import tech.pegasys.teku.network.p2p.peer.SimplePeerSelectionStrategy;
// import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy;
// import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
// import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
// import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
// import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
// import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
// import tech.pegasys.teku.networking.p2p.peer.Peer;
// import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
// import tech.pegasys.teku.spec.Spec;
// import tech.pegasys.teku.spec.SpecFactory;
// import tech.pegasys.teku.storage.store.MemKeyValueStore;
// import tech.pegasys.teku.util.config.Constants;
import org.minima.system.network.base.metrics.NoOpMetricsSystem;
import org.minima.system.network.base.peer.Peer;
import org.minima.system.network.base.peer.PeerSelectionStrategy;
import org.minima.system.network.base.peer.ReputationManager;
import org.minima.system.network.base.peer.SimplePeerSelectionStrategy;
import org.minima.system.network.base.peer.TargetPeerRange;

public class DiscoveryNetworkFactory {

  protected static final Logger LOG = LogManager.getLogger(DiscoveryNetworkFactory.class);
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final int MIN_PORT = 9000;
  private static final int MAX_PORT = 12000;

  private final List<DiscoveryNetwork<Peer>> networks = new ArrayList<>();

  // from tech.pegasys.teku.util.config.Constants
  public final class Constants {
      public static final int    REPUTATION_MANAGER_CAPACITY = 1024;
      public static final int ATTESTATION_SUBNET_COUNT = 64;
  }

  public DiscoveryNetworkBuilder builder() {
    return new DiscoveryNetworkBuilder();
  }

  public void stopAll() throws InterruptedException, ExecutionException, TimeoutException {
    Waiter.waitFor(
        SafeFuture.allOf(networks.stream().map(DiscoveryNetwork::stop).toArray(SafeFuture[]::new)));
  }

  public class DiscoveryNetworkBuilder {
    private final List<String> staticPeers = new ArrayList<>();
    private final List<String> bootnodes = new ArrayList<>();
    private PrivKey privKey;

    private DiscoveryNetworkBuilder() {}

    public DiscoveryNetworkBuilder staticPeer(final String staticPeer) {
      this.staticPeers.add(staticPeer);
      return this;
    }

    public DiscoveryNetworkBuilder bootnode(final String bootnode) {
      this.bootnodes.add(bootnode);
      return this;
    }

    public DiscoveryNetworkBuilder setPrivKey(final PrivKey privKey) {
      this.privKey = privKey;
      return this;
    }
    public DiscoveryNetwork<Peer> buildAndStart(int _port) throws Exception {
      int attempt = 1;
      while (true) {
        final int port;
        if(_port == 0) {
            final Random random = new Random();
            port = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT);
        } else {
          port = _port;
        }
        final DiscoveryConfig discoveryConfig =
            DiscoveryConfig.builder().staticPeers(staticPeers).bootnodes(bootnodes).build();
        final NetworkConfig config =
            NetworkConfig.builder().listenPort(port).networkInterface("0.0.0.0").build();
        final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
        final ReputationManager reputationManager =
            new ReputationManager(
                metricsSystem,
                StubTimeProvider.withTimeInSeconds(1000),
                Constants.REPUTATION_MANAGER_CAPACITY);
        final PeerSelectionStrategy peerSelectionStrategy =
            new SimplePeerSelectionStrategy(new TargetPeerRange(20, 30, 0));

        final DiscoveryNetwork<Peer> network =
            DiscoveryNetwork.create(
                metricsSystem,
                DelayedExecutorAsyncRunner.create(),
                new MemKeyValueStore<>(),
                new LibP2PNetwork(
                    DelayedExecutorAsyncRunner.create(),
                    config,
                    privKey,
                    reputationManager,
                    METRICS_SYSTEM,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    (__1, __2) -> {
                      throw new UnsupportedOperationException();
                    },
                    topic -> true),
                peerSelectionStrategy,
                discoveryConfig,
                config);
        try {
          network.start().get(5, TimeUnit.SECONDS);
          networks.add(network);
          return network;
        } catch (final ExecutionException e) {
          if (e.getCause() instanceof BindException) {
            if (attempt > 10) {
              throw new RuntimeException("Failed to find a free port after multiple attempts", e);
            }
            LOG.info(
                "Port conflict detected, retrying with a new port. Original message: {}",
                e.getMessage());
            attempt++;
            Waiter.waitFor(network.stop());
          } else {
            throw e;
          }
        }
      }
    }
  }
}