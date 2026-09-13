package com.euiseon.friger.history.service;

import java.util.List;
import com.euiseon.friger.history.dao.HistoryDao;
import com.euiseon.friger.history.dto.HistoryEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {
    private final HistoryDao dao;
    public HistoryService(HistoryDao dao) { this.dao = dao; }
    @Transactional(readOnly = true)
    public List<HistoryEntry> recent(int limit) { return dao.findRecent(limit); }
}
