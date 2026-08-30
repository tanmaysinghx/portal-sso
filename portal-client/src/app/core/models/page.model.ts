/** The envelope every paged admin endpoint returns. Mirrors PageResponse<T> on the server. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
