package pl.ldz.example.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.ldz.example.model.ItemFile;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemFileRepository extends JpaRepository<ItemFile, Long> {

  List<ItemFile> findAllByItemId(Long itemId);

  Optional<ItemFile> findByIdAndItemId(Long id, Long itemId);
}
