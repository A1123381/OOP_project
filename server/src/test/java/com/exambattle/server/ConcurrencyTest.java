package com.exambattle.server;

import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 多個房間同時聊天：每人只收到同房訊息，且同房的人看到的順序一致。 */
class ConcurrencyTest extends ServerTestBase {

    @Test
    void fortyPlayersInFourRoomsChatConcurrently() throws Exception {
        int roomCount = 4;
        int playersPerRoom = 10;
        int messagesPerPlayer = 5;

        List<String> codes = new ArrayList<>();
        List<List<TestClient>> rooms = new ArrayList<>();
        for (int r = 0; r < roomCount; r++) {
            TestClient owner = loggedIn("owner" + r);
            codes.add(owner.createRoom().roomCode());
            List<TestClient> members = new ArrayList<>(List.of(owner));
            for (int p = 1; p < playersPerRoom; p++) {
                TestClient player = loggedIn("r" + r + "p" + p);
                player.joinRoom(codes.get(r));
                members.add(player);
            }
            rooms.add(members);
        }
        assertEquals(roomCount, server.roomCount());

        CyclicBarrier start = new CyclicBarrier(roomCount * playersPerRoom);
        int expectedPerPlayer = playersPerRoom * messagesPerPlayer;
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<List<Future<List<String>>>> results = new ArrayList<>();
            for (int r = 0; r < roomCount; r++) {
                List<Future<List<String>>> roomResults = new ArrayList<>();
                for (int p = 0; p < playersPerRoom; p++) {
                    TestClient c = rooms.get(r).get(p);
                    String tag = "r" + r + "p" + p;
                    String code = codes.get(r);
                    Callable<List<String>> task = () -> {
                        start.await(10, TimeUnit.SECONDS);
                        for (int k = 0; k < messagesPerPlayer; k++) {
                            c.send(MessageType.CHAT, new ChatRequest(tag + "#" + k));
                        }
                        List<String> received = new ArrayList<>();
                        while (received.size() < expectedPerPlayer) {
                            ChatBroadcast chat = c.nextOfType(MessageType.CHAT).payloadAs(ChatBroadcast.class);
                            if (!code.equals(chat.roomCode())) {
                                throw new AssertionError(tag + " 收到其他房間的訊息：" + chat);
                            }
                            received.add(chat.text());
                        }
                        return received;
                    };
                    roomResults.add(pool.submit(task));
                }
                results.add(roomResults);
            }

            for (int r = 0; r < roomCount; r++) {
                List<String> first = results.get(r).get(0).get(20, TimeUnit.SECONDS);
                assertEquals(expectedPerPlayer, first.stream().distinct().count());
                String prefix = "r" + r + "p";
                assertTrue(first.stream().allMatch(t -> t.startsWith(prefix)), "只應收到同房訊息");
                for (Future<List<String>> other : results.get(r)) {
                    assertEquals(first, other.get(20, TimeUnit.SECONDS), "同房玩家看到的順序必須一致");
                }
            }
        }
    }
}
