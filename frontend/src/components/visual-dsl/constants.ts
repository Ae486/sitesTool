/**
 * Constants for Visual DSL Editor
 */

/**
 * Container color schemes for visual distinction
 */
export const CONTAINER_COLORS: Record<string, { border: string; bg: string; headerBg: string }> = {
  loop: { border: "#722ed1", bg: "#f9f0ff", headerBg: "#efdbff" },
  loop_array: { border: "#13c2c2", bg: "#e6fffb", headerBg: "#b5f5ec" },
  if_else: { border: "#fa8c16", bg: "#fff7e6", headerBg: "#ffd591" },
};

/**
 * Maximum nesting depth for container steps
 */
export const MAX_NESTING_DEPTH = 4;
