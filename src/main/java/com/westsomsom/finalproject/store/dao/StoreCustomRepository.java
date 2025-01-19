package com.westsomsom.finalproject.store.dao;

import com.westsomsom.finalproject.store.domain.Store;
import com.westsomsom.finalproject.store.dto.SearchRequestDto;
import com.westsomsom.finalproject.store.dto.SearchResponseDto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface StoreCustomRepository {
    Slice<Store> findStoresNoOffset(Integer lastStoreId, Pageable pageable);
    List<SearchResponseDto> searchStore(SearchRequestDto searchRequestDto);
    List<SearchResponseDto> searchStoreCategory(String category, Integer storeId);
//    Page<Store> getAllStores(Pageable pageable);
}
