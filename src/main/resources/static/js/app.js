// Finance RAG Application JavaScript

document.addEventListener('DOMContentLoaded', function() {
    // Get DOM elements
    const uploadForm = document.getElementById('uploadForm');
    const chatForm = document.getElementById('chatForm');
    const uploadBtn = document.getElementById('uploadBtn');
    const askBtn = document.getElementById('askBtn');
    const uploadStatus = document.getElementById('uploadStatus');
    const uploadMessage = document.getElementById('uploadMessage');
    const chatMessages = document.getElementById('chatMessages');
    const questionInput = document.getElementById('questionInput');
    const loadingModal = new bootstrap.Modal(document.getElementById('loadingModal'));
    const loadingText = document.getElementById('loadingText');

    // Upload form handler
    uploadForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        
        const fileInput = document.getElementById('documentFile');
        const file = fileInput.files[0];
        
        if (!file) {
            showUploadStatus('Please select a file to upload.', 'danger');
            return;
        }

        if (!file.name.toLowerCase().endsWith('.pdf')) {
            showUploadStatus('Please select a PDF file.', 'danger');
            return;
        }

        // Show loading
        loadingText.textContent = 'Uploading and processing document...';
        loadingModal.show();
        uploadBtn.disabled = true;

        try {
            const formData = new FormData();
            formData.append('file', file);

            const response = await fetch('/api/upload', {
                method: 'POST',
                body: formData
            });

            const result = await response.json();

            if (result.status === 'success') {
                showUploadStatus(result.message, 'success');
                fileInput.value = ''; // Clear the input
                addSystemMessage('Document uploaded successfully! You can now ask questions about it.');
            } else {
                showUploadStatus(result.message, 'danger');
            }
        } catch (error) {
            console.error('Upload error:', error);
            showUploadStatus('Error uploading document. Please try again.', 'danger');
        } finally {
            loadingModal.hide();
            uploadBtn.disabled = false;
        }
    });

    // Chat form handler
    chatForm.addEventListener('submit', async function(e) {
        e.preventDefault();
        
        const question = questionInput.value.trim();
        if (!question) return;

        // Get selected AI model
        const selectedModel = document.querySelector('input[name="aiModel"]:checked').value;

        // Add user message to chat
        addMessage(question, 'user');
        questionInput.value = '';
        
        // Show loading
        loadingText.textContent = 'Thinking...';
        loadingModal.show();
        askBtn.disabled = true;

        try {
            const formData = new FormData();
            formData.append('question', question);
            formData.append('model', selectedModel);

            const response = await fetch('/api/chat', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();
            
            if (data.status === 'success') {
                addMessage(data.answer, 'bot', data.model);
            } else {
                addMessage('Sorry, I encountered an error processing your question.', 'bot', 'Error');
            }
        } catch (error) {
            addMessage('Sorry, I encountered an error. Please try again.', 'bot', 'Error');
        } finally {
            loadingModal.hide();
            askBtn.disabled = false;
        }
    });

    // Helper functions
    function showUploadStatus(message, type) {
        uploadMessage.textContent = message;
        uploadStatus.className = `mt-3 alert alert-${type}`;
        uploadStatus.style.display = 'block';
        
        // Auto-hide success messages after 5 seconds
        if (type === 'success') {
            setTimeout(() => {
                uploadStatus.style.display = 'none';
            }, 5000);
        }
    }

    function addMessage(content, type, modelUsed) {
        // Remove welcome message if it exists
        const welcomeMessage = chatMessages.querySelector('.welcome-message');
        if (welcomeMessage) {
            welcomeMessage.remove();
        }

        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;
        
        const now = new Date();
        const timeString = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        
        let modelBadge = '';
        if (type === 'bot' && modelUsed) {
            const badgeClass = modelUsed.includes('Gemini') ? 'bg-success' : 'bg-primary';
            modelBadge = `<span class="badge ${badgeClass} mb-2">${modelUsed}</span><br>`;
        }
        
        messageDiv.innerHTML = `
            ${modelBadge}
            <div class="message-content">${content.replace(/\n/g, '<br>')}</div>
            <div class="message-time">${timeString}</div>
        `;
        
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function addSystemMessage(content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message system text-center mb-3';
        messageDiv.innerHTML = `
            <div class="alert alert-info d-inline-block">
                <i class="fas fa-info-circle me-2"></i>
                ${content}
            </div>
        `;
        
        chatMessages.appendChild(messageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function formatMessage(content) {
        // Basic formatting for better readability
        return content
            .replace(/\n/g, '<br>')
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>');
    }

    // Focus on question input when page loads
    questionInput.focus();

    // Enable drag and drop for file upload
    const fileInput = document.getElementById('documentFile');
    const uploadCard = fileInput.closest('.card');

    uploadCard.addEventListener('dragover', function(e) {
        e.preventDefault();
        uploadCard.style.borderColor = '#007bff';
        uploadCard.style.backgroundColor = '#e7f3ff';
    });

    uploadCard.addEventListener('dragleave', function(e) {
        e.preventDefault();
        uploadCard.style.borderColor = '';
        uploadCard.style.backgroundColor = '';
    });

    uploadCard.addEventListener('drop', function(e) {
        e.preventDefault();
        uploadCard.style.borderColor = '';
        uploadCard.style.backgroundColor = '';
        
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            fileInput.files = files;
        }
    });
}); 