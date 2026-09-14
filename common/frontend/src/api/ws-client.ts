/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: WSS client to /ws/opzhub with reconnect and heartbeat.
 */
const DEFAULT_SOCKET_PATH = "/ws/opzhub";
const HEARTBEAT_INTERVAL_MS = 25000;
const RECONNECT_DELAY_MS = 3000;
const HEARTBEAT_PAYLOAD = "ping";

/**
 * Kernel WebSocket client. disconnect() stops reconnect so the socket and
 * timers cannot outlive the caller.
 */
export class WsClient {
  private socket: WebSocket | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private isDisconnecting = false;

  constructor(private path: string = DEFAULT_SOCKET_PATH) {}

  /**
   * Opens the socket and starts heartbeat. Safe to call again after disconnect.
   */
  connect(): void {
    this.isDisconnecting = false;
    this.clearTimers();
    this.closeSocket();

    const socketScheme = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${socketScheme}://${window.location.host}${this.path}`);
    this.socket = socket;

    socket.onopen = () => {
      if (this.socket !== socket) {
        return;
      }
      this.heartbeatTimer = setInterval(() => {
        if (this.socket?.readyState === WebSocket.OPEN) {
          this.socket.send(HEARTBEAT_PAYLOAD);
        }
      }, HEARTBEAT_INTERVAL_MS);
    };

    socket.onclose = () => {
      if (this.socket !== socket) {
        return;
      }
      this.clearHeartbeat();
      this.socket = null;
      if (this.isDisconnecting) {
        return;
      }
      this.reconnectTimer = setTimeout(() => this.connect(), RECONNECT_DELAY_MS);
    };
  }

  /**
   * Closes the socket and cancels heartbeat and reconnect timers.
   */
  disconnect(): void {
    this.isDisconnecting = true;
    this.clearTimers();
    this.closeSocket();
  }

  private closeSocket(): void {
    if (!this.socket) {
      return;
    }
    this.socket.onopen = null;
    this.socket.onclose = null;
    this.socket.onerror = null;
    this.socket.onmessage = null;
    if (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING) {
      this.socket.close();
    }
    this.socket = null;
  }

  private clearHeartbeat(): void {
    if (this.heartbeatTimer !== null) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private clearTimers(): void {
    this.clearHeartbeat();
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}
