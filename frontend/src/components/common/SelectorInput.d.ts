interface SelectorInputProps {
    value?: string;
    onChange: (value: string) => void;
    placeholder?: string;
    size?: "small" | "middle" | "large";
    /** Show the auto-parser input below */
    showAutoParser?: boolean;
    /** Disable the input */
    disabled?: boolean;
}
/**
 * SelectorInput - Input for CSS selectors with optional HTML auto-parser
 */
declare function SelectorInput({ value, onChange, placeholder, size, showAutoParser, disabled, }: SelectorInputProps): import("react/jsx-runtime").JSX.Element;
declare const _default: import("react").MemoExoticComponent<typeof SelectorInput>;
export default _default;
