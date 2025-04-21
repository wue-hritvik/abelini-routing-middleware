package com.abelini_routing_middleware;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

@Repository
public interface DdSeoUrlRepository extends JpaRepository<DdSeoUrl, Integer> {

    List<DdSeoUrl> findByKeywordAndStoreIdAndLanguageId(String keyword, int storeId, int languageId);

    List<DdSeoUrl> findByKeyAndStoreIdAndLanguageId(String key, int storeId, int languageId);

    List<DdSeoUrl> findByValueAndStoreIdAndLanguageId(String valuePart, int storeId, int languageId);

    List<DdSeoUrl> findAllByKeywordInAndStoreIdAndLanguageId(List<String> pathParts, int storeId, int languageId);

    List<DdSeoUrl> findAllByValueInAndStoreIdAndLanguageId(Set<String> allValueParts, int storeId, int languageId);
}
