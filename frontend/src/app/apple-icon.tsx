import { ImageResponse } from "next/og";

export const runtime = "edge";
export const contentType = "image/png";
export const size = {
  width: 180,
  height: 180
};

export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          alignItems: "center",
          background: "linear-gradient(145deg, #0f4c81, #1d77bd 58%, #f39c3d)",
          borderRadius: 40,
          color: "white",
          display: "flex",
          fontFamily: "sans-serif",
          fontSize: 72,
          fontWeight: 800,
          height: "100%",
          justifyContent: "center",
          letterSpacing: -3,
          width: "100%"
        }}
      >
        FG
      </div>
    ),
    size
  );
}