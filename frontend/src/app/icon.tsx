import { ImageResponse } from "next/og";

export const runtime = "edge";
export const contentType = "image/png";
export const size = {
  width: 512,
  height: 512
};

export default function Icon() {
  return new ImageResponse(
    (
      <div
        style={{
          alignItems: "center",
          background: "linear-gradient(135deg, #0f4c81, #1d77bd 55%, #f39c3d)",
          color: "white",
          display: "flex",
          fontFamily: "sans-serif",
          fontSize: 180,
          fontWeight: 800,
          height: "100%",
          justifyContent: "center",
          letterSpacing: -8,
          width: "100%"
        }}
      >
        FG
      </div>
    ),
    size
  );
}