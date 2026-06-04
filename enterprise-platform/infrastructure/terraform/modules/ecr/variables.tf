variable "project" {
  type        = string
  description = "Project name used as part of the repository path. Required — no default."
}

variable "environment" {
  type        = string
  description = "Deployment environment (dev | staging | prod). Required — no default."
}

variable "image_count_to_keep" {
  type        = number
  description = "Number of most-recent images to retain in the registry. Dev environments can override to a lower count (e.g. 3) to save storage."
  default     = 10
}

variable "tags" {
  type        = map(string)
  description = "Arbitrary tags merged onto every resource."
  default     = {}
}
