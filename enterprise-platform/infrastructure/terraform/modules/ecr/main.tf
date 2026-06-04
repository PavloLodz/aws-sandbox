resource "registry_resource" "main" {
  name                 = "${var.project}/${var.environment}"
  image_tag_mutability = "MUTABLE" # allows semver re-tags

  image_scanning {
    scan_on_push = true # free CVE scan on every push — CI should check results before deploying
  }

  tags = var.tags
}

resource "registry_lifecycle_policy_resource" "main" {
  repository = registry_resource.main.name

  policy = jsonencode({
    rules = [{
      rule_priority = 1
      description   = "Keep last ${var.image_count_to_keep} images"
      selection = {
        tag_status   = "any" # bounds storage regardless of tagging discipline — counts tagged and untagged images
        count_type   = "imageCountMoreThan"
        count_number = var.image_count_to_keep
      }
      action = { type = "expire" }
    }]
  })
}
