package pl.ldz.example.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "item_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemFile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_id", nullable = false)
  private Item item;

  @Column(nullable = false)
  private String originalName;

  @Column(nullable = false, unique = true)
  private String s3Key;

  @Column(nullable = false)
  private String contentType;

  @Column(nullable = false)
  private Long sizeBytes;

  @Column(nullable = false, updatable = false)
  private Instant uploadedAt;
}
