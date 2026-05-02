package pl.ldz.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.ldz.example.dto.ItemFileResponse;
import pl.ldz.example.model.Item;
import pl.ldz.example.model.ItemFile;
import pl.ldz.example.repository.ItemFileRepository;
import pl.ldz.example.repository.ItemRepository;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ItemFileService {

  private final ItemRepository itemRepository;
  private final ItemFileRepository itemFileRepository;
  private final S3Service s3Service;

  public List<ItemFileResponse> listFiles(Long itemId) {
    requireItem(itemId);
    return itemFileRepository.findAllByItemId(itemId).stream()
        .map(ItemFileResponse::from)
        .toList();
  }

  @Transactional
  public ItemFileResponse upload(Long itemId, MultipartFile file) {
    Item item = requireItem(itemId);

    String s3Key = s3Service.upload(itemId, file);

    ItemFile record = ItemFile.builder()
        .item(item)
        .originalName(file.getOriginalFilename())
        .s3Key(s3Key)
        .contentType(file.getContentType() != null
            ? file.getContentType()
            : MediaType.APPLICATION_OCTET_STREAM_VALUE)
        .sizeBytes(file.getSize())
        .uploadedAt(Instant.now())
        .build();

    return ItemFileResponse.from(itemFileRepository.save(record));
  }

  public ResponseEntity<byte[]> download(Long itemId, Long fileId) {
    ItemFile record = requireFile(itemId, fileId);
    ResponseBytes<GetObjectResponse> s3Response = s3Service.download(record.getS3Key());

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + record.getOriginalName() + "\"")
        .contentType(MediaType.parseMediaType(record.getContentType()))
        .body(s3Response.asByteArray());
  }

  @Transactional
  public void delete(Long itemId, Long fileId) {
    ItemFile record = requireFile(itemId, fileId);
    s3Service.delete(record.getS3Key());
    itemFileRepository.delete(record);
  }

  private Item requireItem(Long itemId) {
    return itemRepository.findById(itemId)
        .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
  }

  private ItemFile requireFile(Long itemId, Long fileId) {
    return itemFileRepository.findByIdAndItemId(fileId, itemId)
        .orElseThrow(() -> new NoSuchElementException(
            "File " + fileId + " not found for item " + itemId));
  }
}
