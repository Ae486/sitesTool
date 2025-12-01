interface NodePaletteProps {
    onAddNode: (stepType: string, position?: {
        x: number;
        y: number;
    }) => void;
}
export default function NodePalette({ onAddNode }: NodePaletteProps): import("react/jsx-runtime").JSX.Element;
export {};
