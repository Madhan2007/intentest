/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Mixes two hex colors for muted licensed launcher tiles.
 */
import {
  COLOR_MIX_RATIO_MAX,
  COLOR_MIX_RATIO_MIN,
  HEX_BLUE_OFFSET,
  HEX_CHANNEL_LENGTH,
  HEX_CHANNEL_RADIX,
  HEX_COLOR_PATTERN,
  HEX_GREEN_OFFSET,
  HEX_RED_OFFSET,
  LICENSED_ICON_GREY_MIX_COLOR,
  LICENSED_ICON_GREY_MIX_RATIO,
} from "./dashboardConstants";

interface RgbColor {
  red: number;
  green: number;
  blue: number;
}

const licensedIconColorCache = new Map<string, string>();

/**
 * Mixes a brand color toward slate grey for licensed, not-installed tiles.
 *
 * @param hexColor catalog icon background
 * @returns muted hex color between the brand color and grey
 */
export function licensedLauncherIconColor(hexColor: string): string {
  const cachedColor = licensedIconColorCache.get(hexColor);
  if (cachedColor) {
    return cachedColor;
  }
  const mixedColor = mixHexColors(hexColor, LICENSED_ICON_GREY_MIX_COLOR, LICENSED_ICON_GREY_MIX_RATIO);
  licensedIconColorCache.set(hexColor, mixedColor);
  return mixedColor;
}

/**
 * Mixes two #rrggbb colors.
 *
 * @param startHexColor start color
 * @param endHexColor end color
 * @param endColorRatio how far to move toward the end color
 * @returns mixed hex color
 */
export function mixHexColors(startHexColor: string, endHexColor: string, endColorRatio: number): string {
  const startColor = parseHexColor(startHexColor);
  const endColor = parseHexColor(endHexColor);
  if (!startColor || !endColor) {
    return startHexColor;
  }
  const clampedEndColorRatio = Math.min(COLOR_MIX_RATIO_MAX, Math.max(COLOR_MIX_RATIO_MIN, endColorRatio));
  return rgbToHex(
    mixChannel(startColor.red, endColor.red, clampedEndColorRatio),
    mixChannel(startColor.green, endColor.green, clampedEndColorRatio),
    mixChannel(startColor.blue, endColor.blue, clampedEndColorRatio)
  );
}

function parseHexColor(hexColor: string): RgbColor | null {
  const normalizedHex = hexColor.trim().replace("#", "");
  if (!HEX_COLOR_PATTERN.test(normalizedHex)) {
    return null;
  }
  return {
    red: Number.parseInt(normalizedHex.slice(HEX_RED_OFFSET, HEX_RED_OFFSET + HEX_CHANNEL_LENGTH), HEX_CHANNEL_RADIX),
    green: Number.parseInt(
      normalizedHex.slice(HEX_GREEN_OFFSET, HEX_GREEN_OFFSET + HEX_CHANNEL_LENGTH),
      HEX_CHANNEL_RADIX
    ),
    blue: Number.parseInt(normalizedHex.slice(HEX_BLUE_OFFSET, HEX_BLUE_OFFSET + HEX_CHANNEL_LENGTH), HEX_CHANNEL_RADIX),
  };
}

function mixChannel(startChannel: number, endChannel: number, endColorRatio: number): number {
  return Math.round(startChannel + (endChannel - startChannel) * endColorRatio);
}

function rgbToHex(red: number, green: number, blue: number): string {
  return `#${channelToHex(red)}${channelToHex(green)}${channelToHex(blue)}`;
}

function channelToHex(channel: number): string {
  return channel.toString(HEX_CHANNEL_RADIX).padStart(HEX_CHANNEL_LENGTH, "0");
}
