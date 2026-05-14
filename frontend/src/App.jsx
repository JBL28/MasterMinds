import { Client } from '@stomp/stompjs'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { create } from 'zustand'
import './App.css'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const useGameStore = create((set) => ({
  room: null,
  playerToken: localStorage.getItem('playerToken') ?? '',
  roomCode: localStorage.getItem('roomCode') ?? '',
  assignment: null,
  revealedMessages: [],
  events: [],
  setSession: (roomCode, playerToken) => {
    localStorage.setItem('roomCode', roomCode)
    localStorage.setItem('playerToken', playerToken)
    set({ roomCode, playerToken })
  },
  setRoom: (room) => set({ room }),
  setAssignment: (assignment) => set({ assignment }),
  setRevealedMessages: (messages) => set({ revealedMessages: messages }),
  addEvent: (event) =>
    set((state) => ({ events: [event, ...state.events].slice(0, 8) })),
}))

const phaseLabels = {
  DAY_CHAT: 'Day parley',
  VOTE_NOMINATE: 'Accusation',
  FINAL_SPEECH: 'Final words',
  VOTE_GUILTY: 'Judgment',
  NIGHT: 'Night',
  GAME_OVER: 'Game over',
}

const roleClasses = {
  MAFIA: 'danger',
  DETECTIVE: 'steel',
  DOCTOR: 'green',
  FOOL: 'gold',
  HYPNOTIST: 'violet',
  LAWYER: 'amber',
  CITIZEN: 'plain',
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
    ...options,
  })
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(data?.detail ?? data?.title ?? 'Request failed.')
  }
  return data
}

function App() {
  const {
    room,
    roomCode,
    playerToken,
    assignment,
    revealedMessages,
    events,
    setSession,
    setRoom,
    setAssignment,
    setRevealedMessages,
    addEvent,
  } = useGameStore()
  const [name, setName] = useState('')
  const [joinCode, setJoinCode] = useState(roomCode)
  const [message, setMessage] = useState('')
  const [replacementMessage, setReplacementMessage] = useState('')
  const [targetToken, setTargetToken] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const alivePlayers = useMemo(
    () => room?.players?.filter((player) => player.alive) ?? [],
    [room],
  )
  const self = useMemo(
    () => room?.players?.find((player) => player.playerToken === playerToken),
    [room, playerToken],
  )
  const selectableTargets = alivePlayers.filter(
    (player) => player.playerToken !== playerToken,
  )

  const refreshRoom = useCallback(
    async (code = roomCode, token = playerToken) => {
      if (!code) return
      const nextRoom = await request(`/api/rooms/${code}`)
      setRoom(nextRoom)
      if (token && nextRoom.status === 'IN_GAME') {
        const nextAssignment = await request(
          `/api/rooms/${code}/players/${token}/assignment`,
        )
        setAssignment(nextAssignment)
      }
    },
    [playerToken, roomCode, setAssignment, setRoom],
  )

  useEffect(() => {
    if (!roomCode) return
    refreshRoom(roomCode, playerToken)
    const timer = window.setInterval(() => refreshRoom(roomCode, playerToken), 3000)
    return () => window.clearInterval(timer)
  }, [playerToken, refreshRoom, roomCode])

  useEffect(() => {
    if (!roomCode) return

    const client = new Client({
      brokerURL: `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`,
      reconnectDelay: 2500,
      onConnect: () => {
        client.subscribe(`/topic/rooms/${roomCode}/messages`, (frame) => {
          const payload = JSON.parse(frame.body)
          setRevealedMessages(payload.messages ?? [])
          addEvent({ title: 'Messages revealed', body: 'The table has spoken.' })
          refreshRoom(roomCode, playerToken)
        })
        client.subscribe(`/topic/rooms/${roomCode}/game`, (frame) => {
          const payload = JSON.parse(frame.body)
          addEvent({ title: payload.type ?? 'Game event', body: payload.message ?? '' })
          refreshRoom(roomCode, playerToken)
        })
      },
    })
    client.activate()
    return () => client.deactivate()
  }, [addEvent, playerToken, refreshRoom, roomCode, setRevealedMessages])

  async function run(action) {
    setBusy(true)
    setError('')
    try {
      await action()
    } catch (exception) {
      setError(exception.message)
    } finally {
      setBusy(false)
    }
  }

  async function createRoom() {
    await run(async () => {
      const created = await request('/api/rooms', { method: 'POST' })
      setJoinCode(created.roomCode)
      const joined = await request(`/api/rooms/${created.roomCode}/join`, {
        method: 'POST',
        body: JSON.stringify({ name: name || 'Host' }),
      })
      setSession(created.roomCode, joined.playerToken)
      await refreshRoom(created.roomCode, joined.playerToken)
    })
  }

  async function joinRoom() {
    await run(async () => {
      const joined = await request(`/api/rooms/${joinCode}/join`, {
        method: 'POST',
        body: JSON.stringify({ name: name || 'Player' }),
      })
      setSession(joined.roomCode, joined.playerToken)
      await refreshRoom(joined.roomCode, joined.playerToken)
    })
  }

  async function postAndRefresh(path, body) {
    const result = await request(path, {
      method: 'POST',
      body: JSON.stringify(body),
    })
    if (result?.messages) {
      setRevealedMessages(result.messages)
    }
    await refreshRoom()
    return result
  }

  const canStart = room?.status === 'LOBBY' && self?.host
  const canAdvance = room?.status === 'IN_GAME' && self?.host
  const role = assignment?.role
  const phase = room?.gamePhase

  return (
    <main className="shell">
      <section className="topbar">
        <div>
          <p className="eyebrow">Masterminds</p>
          <h1>Backroom verdict</h1>
        </div>
        <div className="room-badge">
          <span>Room</span>
          <strong>{roomCode || '------'}</strong>
        </div>
      </section>

      <section className="layout">
        <aside className="panel lobby-panel">
          <h2>Seat</h2>
          <label>
            Name
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label>
            Code
            <input
              value={joinCode}
              onChange={(event) => setJoinCode(event.target.value.toUpperCase())}
              maxLength={6}
            />
          </label>
          <div className="button-row">
            <button type="button" onClick={createRoom} disabled={busy}>
              Create
            </button>
            <button type="button" onClick={joinRoom} disabled={busy || !joinCode}>
              Join
            </button>
          </div>
          {error && <p className="error">{error}</p>}
        </aside>

        <section className="panel table-panel">
          <div className="phase-line">
            <div>
              <span className="eyebrow">Phase</span>
              <h2>{phaseLabels[phase] ?? room?.status ?? 'No room'}</h2>
            </div>
            <div className="turn-chip">Turn {room?.dayTurn ?? 0}</div>
          </div>

          {assignment && (
            <article className={`role-card ${roleClasses[role] ?? 'plain'}`}>
              <span>Your card</span>
              <strong>{assignment.displayName}</strong>
              <p>{assignment.description}</p>
              {role === 'LAWYER' && assignment.lawyerClientToken && (
                <small>Client: {shortToken(assignment.lawyerClientToken)}</small>
              )}
            </article>
          )}

          <div className="actions">
            {canStart && (
              <button
                type="button"
                onClick={() =>
                  run(() =>
                    postAndRefresh(`/api/rooms/${roomCode}/start`, { playerToken }),
                  )
                }
                disabled={busy}
              >
                Start
              </button>
            )}
            {canAdvance && (
              <button
                type="button"
                onClick={() =>
                  run(() =>
                    postAndRefresh(`/api/rooms/${roomCode}/phase/advance`, {
                      playerToken,
                    }),
                  )
                }
                disabled={busy}
              >
                Advance
              </button>
            )}
          </div>

          {room?.result && (
            <div className="verdict">
              <span>Result</span>
              <strong>{room.result}</strong>
              {room.lawyerWin && <em>Lawyer side win</em>}
            </div>
          )}

          <PlayerTable players={room?.players ?? []} currentToken={playerToken} />
        </section>

        <aside className="panel console-panel">
          <h2>Moves</h2>
          {phase === 'DAY_CHAT' && (
            <DayChatForm
              role={role}
              message={message}
              setMessage={setMessage}
              replacementMessage={replacementMessage}
              setReplacementMessage={setReplacementMessage}
              targetToken={targetToken}
              setTargetToken={setTargetToken}
              targets={selectableTargets}
              onSubmit={() =>
                run(() =>
                  postAndRefresh(`/api/rooms/${roomCode}/day-messages`, {
                    playerToken,
                    message,
                  }).then(() => setMessage('')),
                )
              }
              onHypnotize={() =>
                run(() =>
                  postAndRefresh(`/api/rooms/${roomCode}/hypnotize`, {
                    playerToken,
                    targetToken,
                    message,
                    replacementMessage,
                  }).then(() => {
                    setMessage('')
                    setReplacementMessage('')
                  }),
                )
              }
              disabled={busy || !playerToken}
            />
          )}

          {phase === 'VOTE_NOMINATE' && (
            <TargetAction
              label="Nominate"
              targets={alivePlayers}
              targetToken={targetToken}
              setTargetToken={setTargetToken}
              onSubmit={() =>
                run(() =>
                  postAndRefresh(`/api/rooms/${roomCode}/vote/nominate`, {
                    playerToken,
                    targetToken,
                  }),
                )
              }
            />
          )}

          {phase === 'FINAL_SPEECH' && (
            <button
              type="button"
              onClick={() =>
                run(() =>
                  postAndRefresh(`/api/rooms/${roomCode}/vote/last-words/complete`, {
                    playerToken,
                  }),
                )
              }
            >
              Seal words
            </button>
          )}

          {phase === 'VOTE_GUILTY' && (
            <div className="split-buttons">
              <button
                type="button"
                onClick={() =>
                  run(() =>
                    postAndRefresh(`/api/rooms/${roomCode}/vote/guilty`, {
                      playerToken,
                      guilty: true,
                    }),
                  )
                }
              >
                Guilty
              </button>
              <button
                type="button"
                className="ghost"
                onClick={() =>
                  run(() =>
                    postAndRefresh(`/api/rooms/${roomCode}/vote/guilty`, {
                      playerToken,
                      guilty: false,
                    }),
                  )
                }
              >
                Spare
              </button>
            </div>
          )}

          {phase === 'NIGHT' && (
            <NightActionForm
              role={role}
              targets={selectableTargets}
              targetToken={targetToken}
              setTargetToken={setTargetToken}
              onSubmit={(action) =>
                run(() =>
                  postAndRefresh(`/api/rooms/${roomCode}/night/${action}`, {
                    playerToken,
                    targetToken,
                  }),
                )
              }
            />
          )}
        </aside>
      </section>

      <section className="lower-grid">
        <MessageLog messages={revealedMessages} />
        <EventLog events={events} />
      </section>
    </main>
  )
}

function PlayerTable({ players, currentToken }) {
  return (
    <div className="players">
      {players.map((player) => (
        <div
          className={`player-row ${player.alive ? '' : 'dead'} ${
            player.playerToken === currentToken ? 'self' : ''
          }`}
          key={player.playerToken}
        >
          <span>{player.name}</span>
          <small>{player.host ? 'Host' : shortToken(player.playerToken)}</small>
        </div>
      ))}
    </div>
  )
}

function DayChatForm({
  role,
  message,
  setMessage,
  replacementMessage,
  setReplacementMessage,
  targetToken,
  setTargetToken,
  targets,
  onSubmit,
  onHypnotize,
  disabled,
}) {
  return (
    <div className="stack">
      <textarea
        value={message}
        onChange={(event) => setMessage(event.target.value)}
        placeholder="Statement"
        rows={4}
      />
      {role === 'HYPNOTIST' && (
        <>
          <TargetSelect
            targets={targets}
            targetToken={targetToken}
            setTargetToken={setTargetToken}
          />
          <textarea
            value={replacementMessage}
            onChange={(event) => setReplacementMessage(event.target.value)}
            placeholder="Replacement"
            rows={3}
          />
          <button
            type="button"
            onClick={onHypnotize}
            disabled={disabled || !message || !replacementMessage || !targetToken}
          >
            Hypnotize
          </button>
        </>
      )}
      <button type="button" onClick={onSubmit} disabled={disabled || !message}>
        Send
      </button>
    </div>
  )
}

function TargetAction({ label, targets, targetToken, setTargetToken, onSubmit }) {
  return (
    <div className="stack">
      <TargetSelect
        targets={targets}
        targetToken={targetToken}
        setTargetToken={setTargetToken}
      />
      <button type="button" onClick={onSubmit} disabled={!targetToken}>
        {label}
      </button>
    </div>
  )
}

function NightActionForm({ role, targets, targetToken, setTargetToken, onSubmit }) {
  const action = role === 'MAFIA' ? 'kill' : role === 'DETECTIVE' ? 'investigate' : role === 'DOCTOR' ? 'protect' : ''

  if (!action) {
    return <p className="muted">No night action.</p>
  }

  return (
    <TargetAction
      label={action[0].toUpperCase() + action.slice(1)}
      targets={targets}
      targetToken={targetToken}
      setTargetToken={setTargetToken}
      onSubmit={() => onSubmit(action)}
    />
  )
}

function TargetSelect({ targets, targetToken, setTargetToken }) {
  return (
    <select
      value={targetToken}
      onChange={(event) => setTargetToken(event.target.value)}
    >
      <option value="">Target</option>
      {targets.map((player) => (
        <option value={player.playerToken} key={player.playerToken}>
          {player.name}
        </option>
      ))}
    </select>
  )
}

function MessageLog({ messages }) {
  return (
    <section className="panel">
      <h2>Table talk</h2>
      <div className="message-list">
        {messages.length === 0 && <p className="muted">No revealed messages.</p>}
        {messages.map((message) => (
          <article className="message" key={`${message.playerToken}-${message.submittedAt}`}>
            <strong>{message.playerName}</strong>
            <p>{message.message}</p>
          </article>
        ))}
      </div>
    </section>
  )
}

function EventLog({ events }) {
  return (
    <section className="panel">
      <h2>Ledger</h2>
      <div className="message-list">
        {events.length === 0 && <p className="muted">Quiet.</p>}
        {events.map((event, index) => (
          <article className="message" key={`${event.title}-${index}`}>
            <strong>{event.title}</strong>
            {event.body && <p>{event.body}</p>}
          </article>
        ))}
      </div>
    </section>
  )
}

function shortToken(token) {
  return token ? token.slice(0, 8) : ''
}

export default App
