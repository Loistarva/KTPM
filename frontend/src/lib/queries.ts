import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./api";
import { useNotice } from "../context";
export function useApi<T>(path: string, enabled = true) {
  return useQuery<T, Error>({
    queryKey: [path],
    queryFn: ({ signal }) => api<T>(path, { signal }),
    enabled,
    retry: (count, error) =>
      count < 1 &&
      (!(error instanceof ApiError) ||
        error.status === 0 ||
        error.status >= 500),
  });
}
export function useAction<T = unknown>(
  action: (input: T) => Promise<unknown>,
  success: string,
  after?: () => void,
) {
  const client = useQueryClient();
  const notice = useNotice();
  return useMutation({
    mutationFn: action,
    retry: false,
    onSuccess: async () => {
      await client.invalidateQueries();
      if (success) notice(success);
      after?.();
    },
    onError: (e: Error) => {
      notice(e.message, true);
      if (e instanceof ApiError && (e.status === 409 || e.status === 404))
        void client.invalidateQueries();
    },
  });
}
