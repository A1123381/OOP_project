package com.exambattle.server.dao;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 測試用的 ImageDao：相同 sha256 只存一份；清除時以題庫目前引用的圖片判斷。 */
public final class InMemoryImageDao implements ImageDao {

    private final Map<String, ImageRecord> byId = new LinkedHashMap<>();
    private final Supplier<Set<String>> referenced;

    public InMemoryImageDao(Supplier<Set<String>> referenced) {
        this.referenced = referenced;
    }

    @Override
    public synchronized ImageRecord saveOrGet(ImageRecord image) {
        for (ImageRecord existing : byId.values()) {
            if (existing.sha256().equals(image.sha256())) {
                return existing;
            }
        }
        byId.put(image.id(), image);
        return image;
    }

    @Override
    public synchronized Optional<ImageRecord> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public synchronized Set<String> existing(Collection<String> ids) {
        return ids.stream().filter(byId::containsKey).collect(Collectors.toSet());
    }

    @Override
    public int deleteUnreferencedBefore(Instant cutoff) {
        Set<String> used = referenced.get();
        synchronized (this) {
            int before = byId.size();
            byId.values().removeIf(i -> i.createdAt().isBefore(cutoff) && !used.contains(i.id()));
            return before - byId.size();
        }
    }
}
