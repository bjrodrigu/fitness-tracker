import { useEffect, useRef, useState, type FormEvent } from "react";

interface Props {
  onCode(code: string): void;
  onClose(): void;
}

type Detector = { detect(v: HTMLVideoElement): Promise<{ rawValue: string }[]> };
type DetectorCtor = {
  new (o: { formats: string[] }): Detector;
  getSupportedFormats(): Promise<string[]>;
};

const FORMATS = ["ean_13", "ean_8", "upc_a", "upc_e"];

/**
 * Reads food barcodes with the camera. Uses the browser's own BarcodeDetector where there is one
 * (Chrome on Android) and the ZXing library otherwise (Safari on iPhone), loaded only when needed.
 */
export default function BarcodeScanner({ onCode, onClose }: Props) {
  const video = useRef<HTMLVideoElement>(null);
  const [state, setState] = useState<"starting" | "scanning" | "nocamera" | "denied">("starting");
  const [typed, setTyped] = useState("");
  const done = useRef(false);

  useEffect(() => {
    let stop = () => {};
    let cancelled = false;

    const found = (raw: string) => {
      const code = raw.replace(/\D/g, "");
      if (done.current || code.length < 8) return;
      done.current = true;
      navigator.vibrate?.(40);
      stop();
      onCode(code);
    };

    (async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setState("nocamera");
        return;
      }
      const Native = (window as unknown as { BarcodeDetector?: DetectorCtor }).BarcodeDetector;
      const nativeOk = Native && (await Native.getSupportedFormats().catch((): string[] => [])).includes("ean_13");
      try {
        if (nativeOk) {
          const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false });
          if (cancelled) return stream.getTracks().forEach((t) => t.stop());
          const v = video.current!;
          v.srcObject = stream;
          await v.play();
          setState("scanning");
          const detector = new Native!({ formats: FORMATS });
          const timer = window.setInterval(async () => {
            const hits = await detector.detect(v).catch(() => []);
            if (hits[0]) found(hits[0].rawValue);
          }, 250);
          stop = () => {
            window.clearInterval(timer);
            stream.getTracks().forEach((t) => t.stop());
          };
        } else {
          const [{ BrowserMultiFormatReader }, { BarcodeFormat, DecodeHintType }] = await Promise.all([
            import("@zxing/browser"),
            import("@zxing/library"),
          ]);
          const hints = new Map();
          hints.set(DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E]);
          const reader = new BrowserMultiFormatReader(hints);
          const controls = await reader.decodeFromConstraints(
            { video: { facingMode: "environment" }, audio: false },
            video.current!,
            (result) => result && found(result.getText()),
          );
          if (cancelled) return controls.stop();
          setState("scanning");
          stop = () => controls.stop();
        }
      } catch (e) {
        setState((e as Error).name === "NotAllowedError" ? "denied" : "nocamera");
      }
    })();

    return () => {
      cancelled = true;
      stop();
    };
  }, [onCode]);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const code = typed.replace(/\D/g, "");
    if (code.length >= 8) onCode(code);
  };

  return (
    <div className="scanner" role="dialog" aria-modal="true" aria-label="Scan a barcode">
      <video ref={video} className="scanner-video" playsInline muted />
      <div className="scanner-frame" aria-hidden="true" />
      <div className="scanner-top">
        <button type="button" className="btn" onClick={onClose}>Close</button>
      </div>
      <div className="scanner-bottom">
        <p role="status">
          {state === "starting" && "Starting the camera…"}
          {state === "scanning" && "Point the camera at the barcode."}
          {state === "denied" && "Camera access is off for this site. Allow it in your browser settings, or type the numbers."}
          {state === "nocamera" && "The camera isn't available here. It needs the app's https address. Type the numbers under the barcode instead."}
        </p>
        <form onSubmit={submit} className="scanner-manual">
          <input className="field" inputMode="numeric" placeholder="Barcode numbers" value={typed}
            onChange={(e) => setTyped(e.target.value)} aria-label="Barcode numbers" />
          <button type="submit" className="btn primary">Look up</button>
        </form>
      </div>
    </div>
  );
}
