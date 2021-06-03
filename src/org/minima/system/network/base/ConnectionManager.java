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

import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.minima.system.network.base.metrics.Counter;
import org.minima.system.network.base.metrics.LabelledMetric;
import org.minima.system.network.base.metrics.MetricsSystem;
import org.minima.system.network.base.metrics.TekuMetricCategory;
import org.minima.system.network.base.peer.DisconnectReason;
import org.minima.system.network.base.peer.DiscoveryPeer;
import org.minima.system.network.base.peer.Peer;
// // import org.hyperledger.besu.plugin.services.MetricsSystem;
// // import org.hyperledger.besu.plugin.services.metrics.Counter;
// // import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
// import tech.pegasys.teku.infrastructure.async.AsyncRunner;
// import tech.pegasys.teku.infrastructure.async.Cancellable;
// import tech.pegasys.teku.infrastructure.async.SafeFuture;
// import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
// import tech.pegasys.teku.networking.p2p.connection.PeerPools.PeerPool;
// import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
// import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;
// import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
// import tech.pegasys.teku.networking.p2p.network.PeerAddress;
// import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
// import tech.pegasys.teku.networking.p2p.peer.Peer;
// import tech.pegasys.teku.service.serviceutils.Service;
import org.minima.system.network.base.peer.PeerAddress;
import org.minima.system.network.base.peer.PeerPools;
import org.minima.system.network.base.peer.PeerSelectionStrategy;
import org.minima.system.network.base.peer.PeerPools.PeerPool;

public class ConnectionManager extends Service {
  private static final Logger LOG = LogManager.getLogger(ConnectionManager.class);
  private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration DISCOVERY_INTERVAL = Duration.ofSeconds(30);
  private final AsyncRunner asyncRunner;
  private final P2PNetwork<? extends Peer> network;
  private final Set<PeerAddress> staticPeers;
  private final DiscoveryService discoveryService;
  private final PeerSelectionStrategy peerSelectionStrategy;
  private final Counter attemptedConnectionCounter;
  private final Counter successfulConnectionCounter;
  private final Counter failedConnectionCounter;
  private final PeerPools peerPools = new PeerPools();
  private final Collection<Predicate<DiscoveryPeer>> peerPredicates = new CopyOnWriteArrayList<>();

  private volatile long peerConnectedSubscriptionId;
  private volatile Cancellable periodicPeerSearch;

  public ConnectionManager(
      final MetricsSystem metricsSystem,
      final DiscoveryService discoveryService,
      final AsyncRunner asyncRunner,
      final P2PNetwork<? extends Peer> network,
      final PeerSelectionStrategy peerSelectionStrategy,
      final List<PeerAddress> peerAddresses) {
    this.asyncRunner = asyncRunner;
    this.network = network;
    this.staticPeers = new HashSet<>(peerAddresses);
    this.discoveryService = discoveryService;
    this.peerSelectionStrategy = peerSelectionStrategy;

    final LabelledMetric<Counter> connectionAttemptCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "peer_connection_attempt_count",
            "Total number of outbound connection attempts made",
            "status");
    attemptedConnectionCounter = connectionAttemptCounter.labels("attempted");
    successfulConnectionCounter = connectionAttemptCounter.labels("successful");
    failedConnectionCounter = connectionAttemptCounter.labels("failed");
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.trace("Starting discovery manager");
    synchronized (this) {
      staticPeers.forEach(this::createPersistentConnection);
    }
    periodicPeerSearch =
        asyncRunner.runWithFixedDelay(
            this::searchForPeers,
            DISCOVERY_INTERVAL,
            error -> LOG.error("Error while searching for peers", error));
    connectToKnownPeers();
    searchForPeers();
    peerConnectedSubscriptionId = network.subscribeConnect(this::onPeerConnected);
    return SafeFuture.COMPLETE;
  }

  private void connectToKnownPeers() {
    peerSelectionStrategy
        .selectPeersToConnect(
            network,
            peerPools,
            () -> discoveryService.streamKnownPeers().filter(this::isPeerValid).collect(toList()))
        .forEach(this::attemptConnection);
  }

  private void searchForPeers() {
    if (!isRunning()) {
      LOG.debug("Not running so not searching for peers");
      return;
    }
    LOG.debug("Searching for peers");
    discoveryService
        .searchForPeers()
        .orTimeout(10, TimeUnit.SECONDS)
        .finish(
            this::connectToKnownPeers,
            error -> {
              LOG.debug("Discovery failed", error);
              connectToKnownPeers();
            });
  }

  private void attemptConnection(final PeerAddress peerAddress) {
    LOG.debug("Attempting to connect to {}", peerAddress.getId());
    attemptedConnectionCounter.inc();
    network
        .connect(peerAddress)
        .finish(
            peer -> {
              LOG.debug("Successfully connected to peer {}", peer.getId());
              successfulConnectionCounter.inc();
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) -> peerPools.forgetPeer(peer.getId()));
            },
            error -> {
              LOG.debug(() -> "Failed to connect to peer: " + peerAddress.getId(), error);
              failedConnectionCounter.inc();
              peerPools.forgetPeer(peerAddress.getId());
            });
  }

  private void onPeerConnected(final Peer peer) {
    peerSelectionStrategy
        .selectPeersToDisconnect(network, peerPools)
        .forEach(
            peerToDrop ->
                peerToDrop.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS).reportExceptions());
  }

  @Override
  protected SafeFuture<?> doStop() {
    network.unsubscribeConnect(peerConnectedSubscriptionId);
    final Cancellable peerSearchTask = this.periodicPeerSearch;
    if (peerSearchTask != null) {
      peerSearchTask.cancel();
    }
    return SafeFuture.COMPLETE;
  }

  public synchronized void addStaticPeer(final PeerAddress peerAddress) {
    if (!staticPeers.contains(peerAddress)) {
      staticPeers.add(peerAddress);
      createPersistentConnection(peerAddress);
    }
  }

  private void createPersistentConnection(final PeerAddress peerAddress) {
    maintainPersistentConnection(peerAddress).reportExceptions();
  }

  private SafeFuture<Peer> maintainPersistentConnection(final PeerAddress peerAddress) {
    if (!isRunning()) {
      // We've been stopped so halt the process.
      return new SafeFuture<>();
    }
    LOG.debug("Connecting to peer {}", peerAddress);
    peerPools.addPeerToPool(peerAddress.getId(), PeerPool.STATIC);
    attemptedConnectionCounter.inc();
    return network
        .connect(peerAddress)
        .thenApply(
            peer -> {
              LOG.debug("Connection to peer {} was successful", peer.getId());
              successfulConnectionCounter.inc();
              peer.subscribeDisconnect(
                  (reason, locallyInitiated) -> {
                    LOG.debug(
                        "Peer {} disconnected. Will try to reconnect in {} sec",
                        peerAddress,
                        RECONNECT_TIMEOUT.toSeconds());
                    asyncRunner
                        .runAfterDelay(
                            () -> maintainPersistentConnection(peerAddress), RECONNECT_TIMEOUT)
                        .reportExceptions();
                  });
              return peer;
            })
        .exceptionallyCompose(
            error -> {
              LOG.debug(
                  "Connection to {} failed: {}. Will retry in {} sec",
                  peerAddress,
                  error,
                  RECONNECT_TIMEOUT.toSeconds());
              failedConnectionCounter.inc();
              return asyncRunner.runAfterDelay(
                  () -> maintainPersistentConnection(peerAddress), RECONNECT_TIMEOUT);
            });
  }

  public void addPeerPredicate(final Predicate<DiscoveryPeer> predicate) {
    peerPredicates.add(predicate);
  }

  private boolean isPeerValid(DiscoveryPeer peer) {
    return !peer.getNodeAddress().getAddress().isAnyLocalAddress()
        && peerPredicates.stream().allMatch(predicate -> predicate.test(peer));
  }
}