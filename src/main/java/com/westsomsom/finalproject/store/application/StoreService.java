package com.westsomsom.finalproject.store.application;

import com.westsomsom.finalproject.store.dao.StoreRepository;
import com.westsomsom.finalproject.store.domain.Store;
import com.westsomsom.finalproject.store.dto.SearchRequestDto;
import com.westsomsom.finalproject.store.dto.SearchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;

//    public Page<Store> getAllStores(Pageable pageable) {
//        return storeRepository.getAllStores(pageable);
//    }

    public Slice<Store> getAllStoresNoOffset(Integer lastStoreId, Pageable pageable) {
        return storeRepository.findStoresNoOffset(lastStoreId, pageable);
    }

    public List<SearchResponseDto> searchStore(SearchRequestDto searchRequestDto) {
        return storeRepository.searchStore(searchRequestDto);
    }

    public List<SearchResponseDto> searchStoreCategory(String category, Integer storeId) {
        return storeRepository.searchStoreCategory(category, storeId);
    }

    public Optional<Store> findById(int storeId){
        return storeRepository.findById(storeId);
    }
}
