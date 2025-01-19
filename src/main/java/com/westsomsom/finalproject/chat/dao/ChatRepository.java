package com.westsomsom.finalproject.chat.dao;

import com.westsomsom.finalproject.chat.domain.Message;
import com.westsomsom.finalproject.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Message, Integer> {
    List<Message> findByStore(Store store);
}
